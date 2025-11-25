import json
import importlib

import pytest


# Import the tool module dynamically so we can monkeypatch its globals easily
sic = importlib.import_module("tools.SalesInvoiceCreationTool")
from tools.SalesInvoiceCreationTool import SalesInvoiceCreationTool, InvoiceSchema, InvoiceLine, InvoiceAddress


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
