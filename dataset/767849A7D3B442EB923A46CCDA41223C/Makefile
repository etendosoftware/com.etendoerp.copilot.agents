
COPILOT_PATH:=../../../com.etendoerp.copilot/evaluation

help:
	@echo "Usage: make [target]"
	@echo "Targets:"
	@echo "  run_bulk_task: Run the bulk task evaluation"

run_bulk_task:
	python3 ${COPILOT_PATH}/bulk_tasks_eval.py --envfile ../../../../gradle.properties --etendo_url http://localhost:8080/etendo --csv 1000-products.csv --template prod-bulk-templates.txt --table m_product
