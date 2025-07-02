
COPILOT_PATH:=../../../com.etendoerp.copilot/evaluation

help:
	@echo "Usage: make [target]"
	@echo "Targets:"
	@echo "  run_bulk_task: Run the bulk task evaluation"

run_bulk_task:
	python3 ${COPILOT_PATH}/bulk_tasks_eval.py --envfile ../../../../gradle.properties --etendo_url http://localhost:8080/etendo --csv InvoiceAgent.csv --template invoice_template_base.txt --table c_invoice --max_tasks=50
