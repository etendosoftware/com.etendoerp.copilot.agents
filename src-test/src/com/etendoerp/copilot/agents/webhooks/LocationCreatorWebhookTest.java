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
  public static final String MISSING_PARAMETER_ADDRESS_1 = "Missing parameter: Address1";

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

    // Setup OBDal static mock
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

  @Test
  public void testCreateLocationSuccessWithoutId() {
    testCreateLocationSuccessWithId(null);
  }

  @Test
  public void testCreateLocationSuccessWithIdNull() {
    testCreateLocationSuccessWithId("null");
  }


  private void testCreateLocationSuccessWithId(String idParameter) {
    // Given
    Map<String, String> parameters = new HashMap<>();
    parameters.put(ADDRESS1_PARAM, TEST_ADDRESS);
    parameters.put(CITY_PARAM, TEST_CITY);
    parameters.put(POSTAL_PARAM, TEST_POSTAL);
    parameters.put(COUNTRY_ISO_CODE_PARAM, TEST_COUNTRY_ISO);

    // Add ID parameter only if it's not null
    if (idParameter != null) {
      parameters.put(ID_PARAM, idParameter);
    }

    Location mockLocation = mock(Location.class);
    Country mockCountry = mock(Country.class);
    OBCriteria<Country> mockCountryCriteria = mock(OBCriteria.class);
    OBProvider mockProvider = mock(OBProvider.class);

    // When
    mockedOBProvider.when(OBProvider::getInstance).thenReturn(mockProvider);
    when(mockProvider.get(Location.class)).thenReturn(mockLocation);

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
    assertEquals(SUCCESS_MESSAGE, responseVars.get(MESSAGE_KEY));
  }

  @Test
  public void testCreateLocationSuccessWithSpecificId() {
    // Given
    Map<String, String> parameters = new HashMap<>();
    parameters.put(ADDRESS1_PARAM, TEST_ADDRESS);
    parameters.put(CITY_PARAM, TEST_CITY);
    parameters.put(POSTAL_PARAM, TEST_POSTAL);
    parameters.put(COUNTRY_ISO_CODE_PARAM, TEST_COUNTRY_ISO);

    Location mockLocation = mock(Location.class);
    Country mockCountry = mock(Country.class);
    OBCriteria<Country> mockCountryCriteria = mock(OBCriteria.class);
    OBProvider mockProvider = mock(OBProvider.class);

    // When
    mockedOBProvider.when(OBProvider::getInstance).thenReturn(mockProvider);
    when(mockProvider.get(Location.class)).thenReturn(mockLocation);
    when(mockLocation.getId()).thenReturn("LOCATION_123");

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
    assertEquals("Location processed successfully: LOCATION_123", responseVars.get(MESSAGE_KEY));
  }

  @Test
  public void testCreateLocationMissingAddress1() {
    // Given
    Map<String, String> parameters = new HashMap<>();
    // Missing ADDRESS1_PARAM
    parameters.put(CITY_PARAM, TEST_CITY);
    parameters.put(POSTAL_PARAM, TEST_POSTAL);
    parameters.put(COUNTRY_ISO_CODE_PARAM, TEST_COUNTRY_ISO);

    Map<String, String> responseVars = new HashMap<>();

    // When
    locationCreatorWebhook.get(parameters, responseVars);

    // Then
    assertTrue(responseVars.containsKey(ERROR_KEY));
    assertEquals(MISSING_PARAMETER_ADDRESS_1, responseVars.get(ERROR_KEY));
  }

  @Test
  public void testCreateLocationMissingCity() {
    // Given
    Map<String, String> parameters = new HashMap<>();
    parameters.put(ADDRESS1_PARAM, TEST_ADDRESS);
    // Missing CITY_PARAM
    parameters.put(POSTAL_PARAM, TEST_POSTAL);
    parameters.put(COUNTRY_ISO_CODE_PARAM, TEST_COUNTRY_ISO);

    Map<String, String> responseVars = new HashMap<>();

    // When
    locationCreatorWebhook.get(parameters, responseVars);

    // Then
    assertTrue(responseVars.containsKey(ERROR_KEY));
    assertEquals("Missing parameter: City", responseVars.get(ERROR_KEY));
  }

  @Test
  public void testCreateLocationMissingPostal() {
    // Given
    Map<String, String> parameters = new HashMap<>();
    parameters.put(ADDRESS1_PARAM, TEST_ADDRESS);
    parameters.put(CITY_PARAM, TEST_CITY);
    // Missing POSTAL_PARAM
    parameters.put(COUNTRY_ISO_CODE_PARAM, TEST_COUNTRY_ISO);

    Map<String, String> responseVars = new HashMap<>();

    // When
    locationCreatorWebhook.get(parameters, responseVars);

    // Then
    assertTrue(responseVars.containsKey(ERROR_KEY));
    assertEquals("Missing parameter: Postal", responseVars.get(ERROR_KEY));
  }

  @Test
  public void testCreateLocationMissingCountryISOCode() {
    // Given
    Map<String, String> parameters = new HashMap<>();
    parameters.put(ADDRESS1_PARAM, TEST_ADDRESS);
    parameters.put(CITY_PARAM, TEST_CITY);
    parameters.put(POSTAL_PARAM, TEST_POSTAL);
    // Missing COUNTRY_ISO_CODE_PARAM

    Map<String, String> responseVars = new HashMap<>();

    // When
    locationCreatorWebhook.get(parameters, responseVars);

    // Then
    assertTrue(responseVars.containsKey(ERROR_KEY));
    assertEquals("Missing parameter: CountryISOCode", responseVars.get(ERROR_KEY));
  }

  @Test
  public void testCreateLocationCountryNotFound() {
    // Given
    Map<String, String> parameters = new HashMap<>();
    parameters.put(ADDRESS1_PARAM, TEST_ADDRESS);
    parameters.put(CITY_PARAM, TEST_CITY);
    parameters.put(POSTAL_PARAM, TEST_POSTAL);
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
    when(mockCountryCriteria.list()).thenReturn(java.util.Collections.emptyList());

    Map<String, String> responseVars = new HashMap<>();
    locationCreatorWebhook.get(parameters, responseVars);

    // Then
    assertTrue(responseVars.containsKey(ERROR_KEY));
    assertEquals("Country not found", responseVars.get(ERROR_KEY));
    assertTrue(responseVars.containsKey(COUNTRIES_KEY));
  }

  @Test
  public void testCreateLocationCountryNotFoundWithCountryList() {
    // Given
    Map<String, String> parameters = new HashMap<>();
    parameters.put(ADDRESS1_PARAM, TEST_ADDRESS);
    parameters.put(CITY_PARAM, TEST_CITY);
    parameters.put(POSTAL_PARAM, TEST_POSTAL);
    parameters.put(COUNTRY_ISO_CODE_PARAM, "INVALID");

    Location mockLocation = mock(Location.class);
    OBProvider mockProvider = mock(OBProvider.class);
    OBCriteria<Country> mockCountryCriteria = mock(OBCriteria.class);

    // Mock countries for the list
    Country mockCountry1 = mock(Country.class);
    Country mockCountry2 = mock(Country.class);
    when(mockCountry1.getId()).thenReturn("US-ID");
    when(mockCountry1.getName()).thenReturn("United States");
    when(mockCountry2.getId()).thenReturn("CA-ID");
    when(mockCountry2.getName()).thenReturn("Canada");

    java.util.List<Country> countryList = java.util.Arrays.asList(mockCountry1, mockCountry2);

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
    assertEquals("Country not found", responseVars.get(ERROR_KEY));
    assertTrue(responseVars.containsKey(COUNTRIES_KEY));
    String countries = responseVars.get(COUNTRIES_KEY);
    assertTrue(countries.contains("US-ID - United States"));
    assertTrue(countries.contains("CA-ID - Canada"));
  }

  @Test
  public void testUpdateLocationSuccess() {
    // Given
    Map<String, String> parameters = new HashMap<>();
    parameters.put(ID_PARAM, TEST_LOCATION_ID);
    parameters.put(ADDRESS1_PARAM, TEST_UPDATED_ADDRESS);
    parameters.put(CITY_PARAM, TEST_UPDATED_CITY);
    parameters.put(POSTAL_PARAM, TEST_UPDATED_POSTAL);
    parameters.put(COUNTRY_ISO_CODE_PARAM, TEST_UPDATED_COUNTRY_ISO);

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
    assertEquals(SUCCESS_MESSAGE, responseVars.get(MESSAGE_KEY));
  }

  @Test
  public void testUpdateLocationNotFound() {
    // Given
    Map<String, String> parameters = new HashMap<>();
    parameters.put(ID_PARAM, "testLocationIdNotFound");
    parameters.put(ADDRESS1_PARAM, TEST_UPDATED_ADDRESS);
    parameters.put(CITY_PARAM, TEST_UPDATED_CITY);
    parameters.put(POSTAL_PARAM, TEST_UPDATED_POSTAL);
    parameters.put(COUNTRY_ISO_CODE_PARAM, TEST_UPDATED_COUNTRY_ISO);

    // When
    mockedOBDal.when(OBDal::getInstance).thenReturn(mock(OBDal.class));
    when(OBDal.getInstance().get(Location.class, "testLocationIdNotFound")).thenReturn(null);
    mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("ETCOPAG_LocNotFound"))
        .thenReturn("Location not found for ID: %s");

    Map<String, String> responseVars = new HashMap<>();
    locationCreatorWebhook.get(parameters, responseVars);

    // Then
    assertTrue(responseVars.containsKey(ERROR_KEY));
    assertEquals("Location not found for ID: testLocationIdNotFound", responseVars.get(ERROR_KEY));
    // Verify that no message key is set since it should exit early
    assertTrue(!responseVars.containsKey(MESSAGE_KEY));
  }

  @Test
  public void testCreateLocationWithEmptyParameters() {
    // Given
    Map<String, String> parameters = new HashMap<>();
    parameters.put(ADDRESS1_PARAM, ""); // Empty string
    parameters.put(CITY_PARAM, TEST_CITY);
    parameters.put(POSTAL_PARAM, TEST_POSTAL);
    parameters.put(COUNTRY_ISO_CODE_PARAM, TEST_COUNTRY_ISO);

    Map<String, String> responseVars = new HashMap<>();

    // When
    locationCreatorWebhook.get(parameters, responseVars);

    // Then
    assertTrue(responseVars.containsKey(ERROR_KEY));
    assertEquals(MISSING_PARAMETER_ADDRESS_1, responseVars.get(ERROR_KEY));
  }

  @Test
  public void testCreateLocationWithNullParameters() {
    // Given
    Map<String, String> parameters = new HashMap<>();
    parameters.put(ADDRESS1_PARAM, null); // null value
    parameters.put(CITY_PARAM, TEST_CITY);
    parameters.put(POSTAL_PARAM, TEST_POSTAL);
    parameters.put(COUNTRY_ISO_CODE_PARAM, TEST_COUNTRY_ISO);

    Map<String, String> responseVars = new HashMap<>();

    // When
    locationCreatorWebhook.get(parameters, responseVars);

    // Then
    assertTrue(responseVars.containsKey(ERROR_KEY));
    assertEquals(MISSING_PARAMETER_ADDRESS_1, responseVars.get(ERROR_KEY));
  }
}