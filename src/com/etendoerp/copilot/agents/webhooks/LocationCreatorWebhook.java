package com.etendoerp.copilot.agents.webhooks;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.common.geography.Country;
import org.openbravo.model.common.geography.Location;

import com.etendoerp.webhookevents.services.BaseWebhookService;

/**
 * Webhook service for handling location-related operations.
 * <p>
 * This service processes incoming webhook requests to create or update location entities
 * based on the provided parameters.
 */
public class LocationCreatorWebhook extends BaseWebhookService {

  private static final Logger LOG = LogManager.getLogger();
  private static final String MESSAGE = "message";

  /**
   * Processes the incoming webhook request to create or update a location.
   *
   * @param parameter
   *     the map of request parameters
   * @param responseVars
   *     the map of response variables to be populated
   */
  @Override
  public void get(Map<String, String> parameter, Map<String, String> responseVars) {
    LOG.debug("Executing WebHook: LocationWebhook");
    for (Map.Entry<String, String> entry : parameter.entrySet()) {
      LOG.debug("Parameter: {} = {}", entry.getKey(), entry.getValue());
    }

    String[] paramNames = { "Address1", "City", "Postal", "CountryISOCode" };
    for (int i = 0; i < paramNames.length; i++) {
      if (StringUtils.isEmpty(parameter.get(paramNames[i]))) {
        responseVars.put("error", String.format("Missing parameter: %s", paramNames[i]));
        return;
      }
    }

    String id = parameter.get("ID");
    String address1 = parameter.get("Address1");
    String city = parameter.get("City");
    String postal = parameter.get("Postal");
    String countryCode = parameter.get("CountryISOCode");

    // CODE to handle the ID for update or create logic
    Location location;
    if (StringUtils.isEmpty(id)) {
      location = OBProvider.getInstance().get(Location.class);
      location.setNewOBObject(true);
    } else {
      location = OBDal.getInstance().get(Location.class, id);
    }
    if (location == null) {
      responseVars.put("error", "Location not found");
      return;
    }
    location.setAddressLine1(address1);
    location.setCityName(city);
    location.setPostalCode(postal);

    OBCriteria<Country> countryCrit = OBDal.getInstance().createCriteria(Country.class);
    countryCrit.add(Restrictions.eq(Country.PROPERTY_ISOCOUNTRYCODE, countryCode));
    countryCrit.setMaxResults(1);
    Country country = (Country) countryCrit.uniqueResult();
    if (country == null) {
      responseVars.put("error", "Country not found");
      StringBuilder countryList = new StringBuilder();
      for (Country c : OBDal.getInstance().createCriteria(Country.class).list()) {
        countryList.append(c.getId()).append(" - ").append(c.getName()).append("\n");
      }
      responseVars.put("countries", countryList.toString());

      return;
    }
    location.setCountry(country);
    OBDal.getInstance().save(location);
    OBDal.getInstance().flush();

    responseVars.put(MESSAGE, String.format("Location processed successfully: %s", location.getId()));
  }
}