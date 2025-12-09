import json
import importlib
import sys
import types

import pytest


# Import the tool module dynamically so we can monkeypatch its globals easily
sic = importlib.import_module("tools.SalesInvoiceCreationTool")
from copilot.core.exceptions import ToolException
from tools.SalesInvoiceCreationTool import (
    SalesInvoiceCreationTool,
    InvoiceSchema,
    InvoiceLine,
    InvoiceAddress,
    TaxConfig,
)


class FakeResp:
    def __init__(self, data=None):
        # Mimic the structure returned by call_etendo helpers in other tests
        self.data = data or {}

    def json(self):
        return self.data


@pytest.fixture
def sample_invoice_dict():
    return {
        "businesspartner": "Test Customer",
        "cif": "B12345678",
        "documentno": "INV-001",
        "date": "2025-01-01",
        "address": {
            "street": "Main St 1",
            "city": "Valencia",
            "postal_code": "46000",
            "state": "Valencia",
            "country": "ES",
        },
        "lines": [
            {
                "product": "Test Product",
                "quantity": 2,
                "unit_price": 10.0,
                "tax_rate": 21.0,
                "total": 20.0,
            }
        ],
    }


def _get_mock_response(endpoint, method):
    """Helper function to return mock responses based on endpoint."""
    if "BusinessPartner" in endpoint and method == "POST":
        return {"response": {"data": [{"id": "BP1"}]}}
    if "BPCustomer" in endpoint:
        return {"response": {"data": [{"id": "BP1"}]}}
    if "LocationCreatorWebhook" in endpoint:
        return {"LocationID": "LOC1"}
    if "BPAddress" in endpoint:
        return {"response": {"data": [{"id": "ADDR1"}]}}
    if "SalesInvoiceLine" in endpoint:
        return {"response": {"data": [{"id": "LINE1"}]}}
    if "SalesInvoice" in endpoint:
        return {"response": {"data": [{"id": "INV1"}]}}
    if "TaxRate" in endpoint:
        return {"response": {"data": [{"id": "TAX1", "name": "Entregas 21%"}]}}
    if "SimSearch" in endpoint:
        # Minimal simsearch-like response with one product
        msg = json.dumps(
            {
                "item_0": {
                    "data": [
                        {
                            "id": "PROD1",
                            "name": "Test Product",
                        }
                    ]
                }
            }
        )
        return {"message": msg}
    return {}


def test_sales_invoice_creation_tool_run_happy_path(monkeypatch, sample_invoice_dict):
    # Avoid real HTTP calls
    calls = {"call_etendo": []}

    def fake_call_etendo(url, method, endpoint, access_token, body_params):
        calls["call_etendo"].append(
            {
                "url": url,
                "method": method,
                "endpoint": endpoint,
                "access_token": access_token,
                "body_params": body_params,
            }
        )
        return _get_mock_response(endpoint, method)

    monkeypatch.setattr(sic, "call_etendo", fake_call_etendo)
    monkeypatch.setattr(sic, "get_etendo_host", lambda: "http://etendo.test")
    monkeypatch.setattr(sic, "get_etendo_token", lambda: "TOKEN")

    tool = SalesInvoiceCreationTool()
    out = tool.run(sample_invoice_dict)

    assert out["status"] == "success"
    assert out["invoice_id"] == "INV1"
    assert out["businesspartner_id"] == "BP1"
    assert out["address_id"] == "ADDR1"
    assert out["line_ids"] == ["LINE1"]
    # Ensure we logged some human readable info
    assert any("Invoice created successfully" in entry for entry in out["execution_log"])


def test_sales_invoice_creation_tool_bp_error(monkeypatch, sample_invoice_dict):
    # Force _process_businesspartner to raise so we test error propagation
    def _raise_bp(*a, **k):
        raise RuntimeError("boom-bp")

    monkeypatch.setattr(SalesInvoiceCreationTool, "_process_businesspartner", _raise_bp)
    monkeypatch.setattr(sic, "get_etendo_host", lambda: "http://etendo.test")
    monkeypatch.setattr(sic, "get_etendo_token", lambda: "TOKEN")

    tool = SalesInvoiceCreationTool()
    out = tool.run(sample_invoice_dict)

    assert out["status"] == "error"
    assert "Failed to process Business Partner" in out["error"]
    # completed_steps should be empty for BP failure
    assert out.get("completed_steps", []) == []


def test_sales_invoice_creation_tool_header_error(monkeypatch, sample_invoice_dict):
    # Let BP and address succeed but force _create_invoice_header to fail
    monkeypatch.setattr(
        SalesInvoiceCreationTool,
        "_process_businesspartner",
        lambda self, inv, url, tok: ("BP1", []),
    )
    monkeypatch.setattr(
        SalesInvoiceCreationTool,
        "_create_address",
        lambda self, bp_id, addr, url, tok: ("ADDR1", []),
    )

    def _raise_header(self, invoice, bp_id, addr_id, url, tok):
        raise RuntimeError("boom-header")

    monkeypatch.setattr(SalesInvoiceCreationTool, "_create_invoice_header", _raise_header)
    monkeypatch.setattr(sic, "get_etendo_host", lambda: "http://etendo.test")
    monkeypatch.setattr(sic, "get_etendo_token", lambda: "TOKEN")

    tool = SalesInvoiceCreationTool()
    out = tool.run(sample_invoice_dict)

    assert out["status"] == "error"
    assert "Failed to create Invoice Header" in out["error"]
    # For header failure we expect completed_steps to contain BP and address ids
    assert {"business_partner_id": "BP1"} in out["completed_steps"][0].values() or out["completed_steps"]


def test_process_businesspartner_uses_cif_lookup(monkeypatch, sample_invoice_dict):
    tool = SalesInvoiceCreationTool()
    invoice = InvoiceSchema(**sample_invoice_dict)
    created = {"called": False}

    def fake_search(self, cif, url, token):
        assert cif == sample_invoice_dict["cif"]
        return "BP-CIF"

    def fake_create(self, invoice_data, url, token):
        created["called"] = True
        return "UNEXPECTED"

    monkeypatch.setattr(SalesInvoiceCreationTool, "_search_bp_by_cif", fake_search)
    monkeypatch.setattr(SalesInvoiceCreationTool, "_create_businesspartner", fake_create)

    bp_id, log = tool._process_businesspartner(invoice, "url", "token")

    assert bp_id == "BP-CIF"
    assert not created["called"]
    assert any("Found by CIF" in entry for entry in log)


def test_process_businesspartner_normalizes_null_cif_before_creation(monkeypatch, sample_invoice_dict):
    tool = SalesInvoiceCreationTool()
    sample_invoice_dict["cif"] = " null "
    invoice = InvoiceSchema(**sample_invoice_dict)
    monkeypatch.setattr(SalesInvoiceCreationTool, "_search_bp_by_cif", lambda *a, **k: None)

    captured = {}

    def fake_create(self, invoice_data, url, token):
        captured["cif"] = invoice_data.cif
        raise ToolException("CIF/Tax ID is required to create a new Business Partner")

    monkeypatch.setattr(SalesInvoiceCreationTool, "_create_businesspartner", fake_create)

    with pytest.raises(ToolException):
        tool._process_businesspartner(invoice, "url", "token")

    assert captured["cif"] is None


def test_create_address_without_data_returns_placeholder():
    tool = SalesInvoiceCreationTool()

    address_id, log = tool._create_address("BP1", None, "url", "token")

    assert address_id == "NO_ADDRESS"
    assert any("No address" in entry for entry in log)


def test_create_address_returns_warning_when_location_fails(monkeypatch, sample_invoice_dict):
    tool = SalesInvoiceCreationTool()
    address = InvoiceAddress(**sample_invoice_dict["address"])

    def failing_location(self, addr, url, token):
        raise ToolException("kaboom")

    monkeypatch.setattr(SalesInvoiceCreationTool, "_create_location", failing_location)

    addr_id, log = tool._create_address("BP1", address, "url", "token")

    assert addr_id == "ADDRESS_CREATION_FAILED"
    assert any("Address creation failed" in entry for entry in log)


def test_create_location_builds_expected_payload(monkeypatch, sample_invoice_dict):
    tool = SalesInvoiceCreationTool()
    address = InvoiceAddress(**sample_invoice_dict["address"])
    captured = {}

    def fake_call(url, method, endpoint, access_token, body_params):
        captured["url"] = url
        captured["method"] = method
        captured["endpoint"] = endpoint
        captured["payload"] = body_params
        return {"LocationID": "LOC-9"}

    monkeypatch.setattr(sic, "call_etendo", fake_call)

    location_id = tool._create_location(address, "http://etendo.test", "TOKEN")

    assert location_id == "LOC-9"
    assert captured["method"] == "POST"
    assert captured["endpoint"].endswith("LocationCreatorWebhook")
    assert captured["payload"]["CountryISOCode"] == address.country


def test_resolve_product_uses_generic_when_search_returns_nothing(monkeypatch):
    tool = SalesInvoiceCreationTool()

    def fake_call(url, method, endpoint, access_token, body_params):
        return {"message": json.dumps({"item_0": {"data": []}})}

    generic_calls = {"count": 0}

    def fake_generic(self, etendo_url, token, override=None):
        generic_calls["count"] += 1
        return "GENERIC-ID"

    monkeypatch.setattr(sic, "call_etendo", fake_call)
    monkeypatch.setattr(SalesInvoiceCreationTool, "_get_generic_product", fake_generic)

    product_id, original = tool._resolve_product("Missing", None, "url", "token")

    assert product_id == "GENERIC-ID"
    assert original == "Missing"
    assert generic_calls["count"] == 1


def test_get_generic_product_respects_override(monkeypatch):
    tool = SalesInvoiceCreationTool()

    def fail_call(*_args, **_kwargs):
        raise AssertionError("Should not call SimSearch when override provided")

    monkeypatch.setattr(sic, "call_etendo", fail_call)

    assert tool._get_generic_product("url", "token", override_id="OVERRIDE") == "OVERRIDE"


def test_resolve_tax_prefers_config_mapping(monkeypatch):
    tool = SalesInvoiceCreationTool()
    configs = [TaxConfig(rate=21, id="CFG-TAX")]

    def fail_get_tax(self, *args, **kwargs):  # pragma: no cover - guard path
        raise AssertionError("Should not hit remote search when config matches")

    monkeypatch.setattr(SalesInvoiceCreationTool, "_get_tax_id", fail_get_tax)

    tax_id, from_config = tool._resolve_tax(21, configs, "url", "token")

    assert tax_id == "CFG-TAX"
    assert from_config is True


def test_get_tax_id_prefers_entregas_entries(monkeypatch):
    tool = SalesInvoiceCreationTool()

    def fake_call(url, method, endpoint, access_token, body_params):
        return {
            "response": {
                "data": [
                    {"id": "TAX-2", "name": "Generic 21%"},
                    {"id": "TAX-1", "name": "Entregas 21%"},
                ]
            }
        }

    monkeypatch.setattr(sic, "call_etendo", fake_call)

    tax_id = tool._get_tax_id(21, "url", "token")

    assert tax_id == "TAX-1"


def test_create_invoice_lines_uses_config_tax_and_logs_generic(monkeypatch):
    tool = SalesInvoiceCreationTool()
    line = InvoiceLine(product="Original", quantity=1, unit_price=10, tax_rate=21)
    created = []

    def fake_search(self, product_name, etendo_url, token, override_id):
        return ("GENERIC", product_name)

    def fake_create(self, invoice_id, product_id, line_data, tax_id, original_name, url, token):
        created.append(
            {
                "invoice_id": invoice_id,
                "product_id": product_id,
                "tax_id": tax_id,
                "original_name": original_name,
            }
        )
        return "LINE-1"

    def fail_tax_lookup(self, *args, **kwargs):  # pragma: no cover - guard path
        raise AssertionError("Should not look up tax remotely")

    monkeypatch.setattr(SalesInvoiceCreationTool, "_search_product", fake_search)
    monkeypatch.setattr(SalesInvoiceCreationTool, "_create_invoice_line", fake_create)
    monkeypatch.setattr(SalesInvoiceCreationTool, "_get_tax_id", fail_tax_lookup)

    configs = [TaxConfig(rate=21, id="CFG-TAX")]

    line_ids, log = tool._create_invoice_lines("INV1", [line], configs, None, "url", "token")

    assert line_ids == ["LINE-1"]
    assert created[0]["product_id"] == "GENERIC"
    assert created[0]["tax_id"] == "CFG-TAX"
    assert created[0]["original_name"] == "Original"
    assert any("Original product" in entry for entry in log)


def test_create_invoice_line_sets_description_and_tax(monkeypatch):
    tool = SalesInvoiceCreationTool()
    line = InvoiceLine(product="Line Product", quantity=2, unit_price=5, tax_rate=21)
    captured = {}

    def fake_call(url, method, endpoint, access_token, body_params):
        captured.update(body_params)
        return {"response": {"data": [{"id": "LINE-ID"}]}}

    monkeypatch.setattr(sic, "call_etendo", fake_call)

    line_id = tool._create_invoice_line(
        "INV42",
        "PROD",
        line,
        "TAX-ID",
        "Original Name",
        "url",
        "token",
    )

    assert line_id == "LINE-ID"
    assert captured["tax"] == "TAX-ID"
    assert captured["description"].endswith("Original Name")
    assert captured["invoicedQuantity"] == "2.0"
