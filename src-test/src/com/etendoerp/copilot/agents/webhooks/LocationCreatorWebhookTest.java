package com.etendoerp.copilot.agents.webhooks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.common.geography.Country;
import org.openbravo.model.common.geography.Location;
import org.openbravo.test.base.TestConstants;

public class LocationCreatorWebhookTest extends WeldBaseTest {

  // Constants to avoid duplication
  private static final String ADDRESS1_PARAM = "Address1";
  private static final String CITY_PARAM = "City";
  private static final String POSTAL_PARAM = "Postal";
  private static final String COUNTRY_ISO_CODE_PARAM = "CountryISOCode";
  private static final String ID_PARAM = "ID";
  private static final String MESSAGE_KEY = "message";
  private static final String ERROR_KEY = "error";
  private static final String COUNTRIES_KEY = "countries";

  private static final String TEST_ADDRESS = "123 Test Street";
  private static final String TEST_CITY = "Test City";
  private static final String TEST_POSTAL = "12345";
  private static final String TEST_COUNTRY_ISO = "US";
  private static final String SUCCESS_MESSAGE = "Location processed successfully: null";

  // Test constants for update operations
  private static final String TEST_LOCATION_ID = "testLocationId";
  private static final String TEST_UPDATED_ADDRESS = "456 Updated Street";
  private static final String TEST_UPDATED_CITY = "Updated City";
  private static final String TEST_UPDATED_POSTAL = "54321";
  private static final String TEST_UPDATED_COUNTRY_ISO = "CA";

  // Error message constants
  private static final String MISSING_PARAMETER_ADDRESS_1 = "Missing parameter: Address1";
  private static final String MISSING_PARAMETER_CITY = "Missing parameter: City";
  private static final String MISSING_PARAMETER_POSTAL = "Missing parameter: Postal";
  private static final String MISSING_PARAMETER_COUNTRY_ISO = "Missing parameter: CountryISOCode";
  private static final String COUNTRY_NOT_FOUND = "Country not found";
  private static final String LOCATION_NOT_FOUND_TEMPLATE = "Location not found for ID: %s";

  // Test ID constants
  private static final String TEST_LOCATION_ID_NOT_FOUND = "testLocationIdNotFound";
  private static final String TEST_LOCATION_123 = "LOCATION_123";
  private static final String SUCCESS_MESSAGE_WITH_ID = "Location processed successfully: LOCATION_123";

  // Country mock constants
  private static final String US_COUNTRY_ID = "US-ID";
  private static final String US_COUNTRY_NAME = "United States";
  private static final String CA_COUNTRY_ID = "CA-ID";
  private static final String CA_COUNTRY_NAME = "Canada";

  @Mock
  private OBDal mockOBDal;

  private LocationCreatorWebhook locationCreatorWebhook;
  private MockedStatic<OBDal> mockedOBDal;
  private MockedStatic<OBProvider> mockedOBProvider;
  private MockedStatic<OBContext> mockedOBContext;
  private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  @Before
  @Override
  public void setUp() throws Exception {
    MockitoAnnotations.openMocks(this);
    locationCreatorWebhook = new LocationCreatorWebhook();

    // Setup static mocks
    mockedOBDal = mockStatic(OBDal.class);
    mockedOBProvider = mockStatic(OBProvider.class);
    mockedOBContext = mockStatic(OBContext.class);
    mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

    mockedOBDal.when(OBDal::getInstance).thenReturn(mockOBDal);

    // Set up admin context
    OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.FB_GRP_ADMIN,
        TestConstants.Clients.FB_GRP, TestConstants.Orgs.ESP_NORTE);
  }

  @After
  public void tearDown() {
    if (mockedOBDal != null) {
      mockedOBDal.close();
    }
    if (mockedOBProvider != null) {
      mockedOBProvider.close();
    }
    if (mockedOBContext != null) {
      mockedOBContext.close();
    }
    if (mockedOBMessageUtils != null) {
      mockedOBMessageUtils.close();
    }
  }

  // --- Utility Methods for Parameter Building (Refactored) ---

  /**
   * Builds the parameters map for location creation or update using specified values.
   */
  private static Map<String, String> buildLocationParameters(
      String id,
      String address,
      String city,
      String postal,
      String countryIso) {

    Map<String, String> parameters = new HashMap<>();
    if (id != null) {
      parameters.put(ID_PARAM, id);
    }
    if (address != null) {
      parameters.put(ADDRESS1_PARAM, address);
    }
    if (city != null) {
      parameters.put(CITY_PARAM, city);
    }
    if (postal != null) {
      parameters.put(POSTAL_PARAM, postal);
    }
    if (countryIso != null) {
      parameters.put(COUNTRY_ISO_CODE_PARAM, countryIso);
    }
    return parameters;
  }

  /**
   * Gets parameters for a successful creation (using TEST constants).
   */
  private static Map<String, String> getParameters() {
    return buildLocationParameters(
        null, // ID nulo para creación
        TEST_ADDRESS,
        TEST_CITY,
        TEST_POSTAL,
        TEST_COUNTRY_ISO
    );
  }

  /**
   * Gets parameters for a successful update (using TEST_UPDATED constants).
   */
  private static Map<String, String> getUpdateParameters(String testLocationId) {
    return buildLocationParameters(
        testLocationId,
        TEST_UPDATED_ADDRESS,
        TEST_UPDATED_CITY,
        TEST_UPDATED_POSTAL,
        TEST_UPDATED_COUNTRY_ISO
    );
  }

  // --- Success Creation Tests (Consolidated) ---

  @Test
  public void testCreateLocationSuccessWithoutId() {
    testCreateLocationSuccessScenario(null, null, SUCCESS_MESSAGE);
  }

  @Test
  public void testCreateLocationSuccessWithIdNull() {
    testCreateLocationSuccessScenario("null", null, SUCCESS_MESSAGE);
  }

  @Test
  public void testCreateLocationSuccessWithSpecificId() {
    testCreateLocationSuccessScenario(null, TEST_LOCATION_123, SUCCESS_MESSAGE_WITH_ID);
  }

  private void testCreateLocationSuccessScenario(String inputIdParam, String mockLocationId, String expectedMessage) {
    // Given
    Map<String, String> parameters = getParameters();

    // Add ID parameter only if it's not null and it's an input ID (not the ID of the saved object)
    if (inputIdParam != null) {
      parameters.put(ID_PARAM, inputIdParam);
    }

    Location mockLocation = mock(Location.class);
    Country mockCountry = mock(Country.class);
    OBCriteria<Country> mockCountryCriteria = mock(OBCriteria.class);
    OBProvider mockProvider = mock(OBProvider.class);

    // When
    mockedOBProvider.when(OBProvider::getInstance).thenReturn(mockProvider);
    when(mockProvider.get(Location.class)).thenReturn(mockLocation);

    // If we are checking the ID in the success message, we must mock the ID of the created Location
    if (mockLocationId != null) {
      when(mockLocation.getId()).thenReturn(mockLocationId);
    }

    mockedOBDal.when(OBDal::getInstance).thenReturn(mock(OBDal.class));
    when(OBDal.getInstance().createCriteria(Country.class)).thenReturn(mockCountryCriteria);
    when(mockCountryCriteria.uniqueResult()).thenReturn(mockCountry);

    Map<String, String> responseVars = new HashMap<>();
    locationCreatorWebhook.get(parameters, responseVars);

    // Then
    verify(mockLocation).setAddressLine1(TEST_ADDRESS);
    verify(mockLocation).setCityName(TEST_CITY);
    verify(mockLocation).setPostalCode(TEST_POSTAL);
    verify(mockLocation).setCountry(mockCountry);
    verify(OBDal.getInstance()).save(mockLocation);
    verify(OBDal.getInstance()).flush();

    assertTrue(responseVars.containsKey(MESSAGE_KEY));
    assertEquals(expectedMessage, responseVars.get(MESSAGE_KEY));
  }


  // --- Missing Parameter Tests ---

  @Test
  public void testCreateLocationMissingAddress1() {
    testMissingParameter(ADDRESS1_PARAM, MISSING_PARAMETER_ADDRESS_1);
  }

  @Test
  public void testCreateLocationMissingCity() {
    testMissingParameter(CITY_PARAM, MISSING_PARAMETER_CITY);
  }

  @Test
  public void testCreateLocationMissingPostal() {
    testMissingParameter(POSTAL_PARAM, MISSING_PARAMETER_POSTAL);
  }

  @Test
  public void testCreateLocationMissingCountryISOCode() {
    testMissingParameter(COUNTRY_ISO_CODE_PARAM, MISSING_PARAMETER_COUNTRY_ISO);
  }

  private void testMissingParameter(String missingParam, String expectedErrorMessage) {
    // Given
    Map<String, String> parameters = getParameters();
    parameters.remove(missingParam); // Remove the parameter

    Map<String, String> responseVars = new HashMap<>();

    // When
    locationCreatorWebhook.get(parameters, responseVars);

    // Then
    assertTrue(responseVars.containsKey(ERROR_KEY));
    assertEquals(expectedErrorMessage, responseVars.get(ERROR_KEY));
  }

  // --- Invalid Parameter Value Tests (Consolidated) ---

  @Test
  public void testCreateLocationWithEmptyAddress() {
    testInvalidParameterValue(ADDRESS1_PARAM, "", MISSING_PARAMETER_ADDRESS_1);
  }

  @Test
  public void testCreateLocationWithNullAddress() {
    testInvalidParameterValue(ADDRESS1_PARAM, null, MISSING_PARAMETER_ADDRESS_1);
  }

  private void testInvalidParameterValue(String invalidParamKey, String invalidValue, String expectedErrorMessage) {
    // Given
    Map<String, String> parameters = getParameters();
    parameters.put(invalidParamKey, invalidValue); // Set the invalid value

    Map<String, String> responseVars = new HashMap<>();

    // When
    locationCreatorWebhook.get(parameters, responseVars);

    // Then
    assertTrue(responseVars.containsKey(ERROR_KEY));
    assertEquals(expectedErrorMessage, responseVars.get(ERROR_KEY));
  }

  // --- Country Not Found Tests ---

  @Test
  public void testCreateLocationCountryNotFound() {
    testCountryNotFound(java.util.Collections.emptyList(), false);
  }

  @Test
  public void testCreateLocationCountryNotFoundWithCountryList() {
    // Mock countries for the list
    Country mockCountry1 = mock(Country.class);
    Country mockCountry2 = mock(Country.class);
    when(mockCountry1.getId()).thenReturn(US_COUNTRY_ID);
    when(mockCountry1.getName()).thenReturn(US_COUNTRY_NAME);
    when(mockCountry2.getId()).thenReturn(CA_COUNTRY_ID);
    when(mockCountry2.getName()).thenReturn(CA_COUNTRY_NAME);

    java.util.List<Country> countryList = java.util.Arrays.asList(mockCountry1, mockCountry2);
    testCountryNotFound(countryList, true);
  }

  private void testCountryNotFound(java.util.List<Country> countryList, boolean validateCountryContent) {
    // Given
    Map<String, String> parameters = getParameters();
    parameters.put(COUNTRY_ISO_CODE_PARAM, "INVALID");

    Location mockLocation = mock(Location.class);
    OBProvider mockProvider = mock(OBProvider.class);
    OBCriteria<Country> mockCountryCriteria = mock(OBCriteria.class);

    // When
    mockedOBProvider.when(OBProvider::getInstance).thenReturn(mockProvider);
    when(mockProvider.get(Location.class)).thenReturn(mockLocation);

    mockedOBDal.when(OBDal::getInstance).thenReturn(mock(OBDal.class));
    when(OBDal.getInstance().createCriteria(Country.class)).thenReturn(mockCountryCriteria);
    when(mockCountryCriteria.uniqueResult()).thenReturn(null);
    when(mockCountryCriteria.list()).thenReturn(countryList);

    Map<String, String> responseVars = new HashMap<>();
    locationCreatorWebhook.get(parameters, responseVars);

    // Then
    assertTrue(responseVars.containsKey(ERROR_KEY));
    assertEquals(COUNTRY_NOT_FOUND, responseVars.get(ERROR_KEY));
    assertTrue(responseVars.containsKey(COUNTRIES_KEY));

    if (validateCountryContent) {
      String countries = responseVars.get(COUNTRIES_KEY);
      assertTrue(countries.contains(US_COUNTRY_ID + " - " + US_COUNTRY_NAME));
      assertTrue(countries.contains(CA_COUNTRY_ID + " - " + CA_COUNTRY_NAME));
    }
  }

  // --- Update Tests ---

  @Test
  public void testUpdateLocationSuccess() {
    // Given
    Map<String, String> parameters = getUpdateParameters(TEST_LOCATION_ID);

    Location mockLocation = mock(Location.class);
    Country mockCountry = mock(Country.class);
    OBCriteria<Country> mockCountryCriteria = mock(OBCriteria.class);

    // When
    mockedOBDal.when(OBDal::getInstance).thenReturn(mock(OBDal.class));
    when(OBDal.getInstance().get(Location.class, TEST_LOCATION_ID)).thenReturn(mockLocation);
    when(OBDal.getInstance().createCriteria(Country.class)).thenReturn(mockCountryCriteria);
    when(mockCountryCriteria.uniqueResult()).thenReturn(mockCountry);

    Map<String, String> responseVars = new HashMap<>();
    locationCreatorWebhook.get(parameters, responseVars);

    // Then
    verify(mockLocation).setAddressLine1(TEST_UPDATED_ADDRESS);
    verify(mockLocation).setCityName(TEST_UPDATED_CITY);
    verify(mockLocation).setPostalCode(TEST_UPDATED_POSTAL);
    verify(mockLocation).setCountry(mockCountry);
    verify(OBDal.getInstance()).save(mockLocation);
    verify(OBDal.getInstance()).flush();

    assertTrue(responseVars.containsKey(MESSAGE_KEY));
    // The message is "Location processed successfully: null" because the location ID is not mocked for the message
    assertEquals(SUCCESS_MESSAGE, responseVars.get(MESSAGE_KEY));
  }

  @Test
  public void testUpdateLocationNotFound() {
    // Given
    Map<String, String> parameters = getUpdateParameters(TEST_LOCATION_ID_NOT_FOUND);

    // When
    mockedOBDal.when(OBDal::getInstance).thenReturn(mock(OBDal.class));
    when(OBDal.getInstance().get(Location.class, TEST_LOCATION_ID_NOT_FOUND)).thenReturn(null);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOPAG_LocNotFound"))
        .thenReturn(LOCATION_NOT_FOUND_TEMPLATE);

    Map<String, String> responseVars = new HashMap<>();
    locationCreatorWebhook.get(parameters, responseVars);

    // Then
    assertTrue(responseVars.containsKey(ERROR_KEY));
    assertEquals(String.format(LOCATION_NOT_FOUND_TEMPLATE, TEST_LOCATION_ID_NOT_FOUND), responseVars.get(ERROR_KEY));
    // Verify that no message key is set since it should exit early
    assertTrue(!responseVars.containsKey(MESSAGE_KEY));
  }
}