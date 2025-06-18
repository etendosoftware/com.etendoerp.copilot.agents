export COPILOT_PATH=../../../com.etendoerp.copilot/evaluation
python3 $COPILOT_PATH/gen_variants.py --columns "Search Key,Name,Category ID,Price List Version,Price" --output prod-requests.txt 1000-products.csv "Ask to the assistant to create a product with the given data"
