import json
from typing import Optional, Annotated, Type

from langchain_core.messages import ToolMessage
from langgraph.types import Command
from pydantic import BaseModel

from copilot.core.langgraph.patterns.langsupervisor_pattern import LangSupervisorState
from copilot.core.tool_input import ToolInput, ToolField
from copilot.core.tool_wrapper import ToolWrapper


class Line(BaseModel):
    name: str
    quantity: int
    price: float
    product_id: Optional[str]

class CreatePurchaseOrderInput(ToolInput):
    business_partner_name: str
    lines: list[Line]


class CreatePurchaseOrder(ToolWrapper):
    """Tool to create an order. readed from OCR data
    """

    name: str = "CreatePurchaseOrder"
    description: str = (
        """Tool to create an order. readed from OCR data"""
    )

    args_schema: Type[ToolInput] = CreatePurchaseOrderInput

    def run(self,  input_params, *args, **kwargs):
        try:
            business_partner_name = input_params.get("business_partner_name")
            lines = input_params.get("lines")
            tool_call_id = input_params.get("tool_call_id")

            from copilot.core.langgraph.members_util import tools_specs
            simSearch = None
            postOrder = None
            postOrderLines = None
            for tool in tools_specs:
                if tool.name == "POST_webhooks_SimSearch":
                    simSearch = tool
                if tool.name == "POST_sws_com_etendoerp_etendorx_datasource_PurchaseOrderLines":
                    postOrderLines = tool
                if tool.name == "POST_sws_com_etendoerp_etendorx_datasource_PurchaseOrder":
                    postOrder = tool
            if simSearch is None or postOrder is None or postOrderLines is None:
                return {"error": "tools not found"}

            bp = simSearch(
                {"body": {"searchTerm": business_partner_name, "entityName": "BusinessPartner"}}
            )
            bp = json.loads(bp)
            bp = json.loads(bp["message"])["data"][0]
            for line in lines:
                prod = simSearch({"body": {"searchTerm": line.name, "entityName": "Product"}})
                prod = json.loads(prod)
                prod = json.loads(prod["message"])["data"][0]
                line.product_id = prod["id"]

            order = postOrder({"body": {"businessPartner": bp["id"]}})
            order = json.loads(order)
            order = order["response"]["data"][0]
            for line in lines:
                postOrderLines(
                    {
                        "body": {
                            "salesOrder": order["id"],
                            "product": line.product_id,
                            "orderedQuantity": line.quantity,
                            "unitPrice": line.price,
                        }
                    }
                )

            return "{'status': 'ok'}"
        except Exception as e:
            return "{'error': '" + str(e) + "'}"

# tools.append(create_order)
