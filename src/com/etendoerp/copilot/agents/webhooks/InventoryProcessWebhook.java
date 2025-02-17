package com.etendoerp.copilot.agents.webhooks;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.webhookevents.data.DefinedWebHook;
import com.etendoerp.webhookevents.data.DefinedWebhookParam;
import com.etendoerp.webhookevents.data.DefinedwebhookRole;
import com.etendoerp.webhookevents.services.BaseWebhookService;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.materialmgmt.InventoryCountProcess;
import org.openbravo.model.materialmgmt.transaction.InventoryCount;

public class InventoryProcessWebhook extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final String MESSAGE = "message";

  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    LOG.debug("Executing WebHook: Inventory");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      LOG.debug("Parameter: {} = {}", entry.getKey(), entry.getValue());
    }

    String[] paramNames = {"inventory_id"};
    for (String paramName : paramNames) {
        if (StringUtils.isEmpty(parameter.get(paramName))) {
            responseVars.put("error", String.format("Missing parameter: %s", paramName));
            return;
        }
    }

    String inventoryId = parameter.get("inventory_id");
    InventoryCount pInventory = OBDal.getInstance().get(InventoryCount.class, inventoryId);
    if (pInventory == null) {
        responseVars.put("error", String.format("Requested Inventory does not exist"));
        return;
    }

    // Process the Physical Inventory using inventoryId
    OBError processInventory = new InventoryCountProcess().processInventory(pInventory, false, true);
    if (processInventory != null
            && StringUtils.equals(processInventory.getType(), "Error")) {
        responseVars.put("error", String.format("Failed to process Inventory: %s", processInventory.getMessage()));
        return;
    }

    responseVars.put(MESSAGE, "Inventory processed successfully.");
  }
}