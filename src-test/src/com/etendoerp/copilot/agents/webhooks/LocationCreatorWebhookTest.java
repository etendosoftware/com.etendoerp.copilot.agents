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
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.common.geography.Country;
import org.openbravo.model.common.geography.Location;
import org.openbravo.test.base.TestConstants;
import org.openbravo.base.weld.test.WeldBaseTest;

public class LocationCreatorWebhookTest extends WeldBaseTest {

    @Mock
    private OBDal mockOBDal;

    private LocationCreatorWebhook locationCreatorWebhook;
    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<OBProvider> mockedOBProvider;
    private MockedStatic<OBContext> mockedOBContext;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        locationCreatorWebhook = new LocationCreatorWebhook();

        // Setup OBDal static mock
        mockedOBDal = mockStatic(OBDal.class);
        mockedOBProvider = mockStatic(OBProvider.class);
        mockedOBContext = mockStatic(OBContext.class);

        mockedOBDal.when(OBDal::getInstance).thenReturn(mockOBDal);

        // Set up admin context
        OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.FB_GRP_ADMIN,
                TestConstants.Clients.FB_GRP, TestConstants.Orgs.ESP_NORTE);
    }

    @After
    public void tearDown() throws Exception {
        if (mockedOBDal != null) {
            mockedOBDal.close();
        }
        if (mockedOBProvider != null) {
            mockedOBProvider.close();
        }
        if (mockedOBContext != null) {
            mockedOBContext.close();
        }
    }

    @Test
    public void testCreateLocationSuccess() {
        // Given
        Map<String, String> parameters = new HashMap<>();
        parameters.put("Address1", "123 Test Street");
        parameters.put("City", "Test City");
        parameters.put("Postal", "12345");
        parameters.put("CountryISOCode", "US");

        Location mockLocation = mock(Location.class);
        Country mockCountry = mock(Country.class);
        OBCriteria<Country> mockCountryCriteria = mock(OBCriteria.class);
        OBProvider mockProvider = mock(OBProvider.class);

        // When
        mockedOBProvider.when(() -> OBProvider.getInstance()).thenReturn(mockProvider);
        when(mockProvider.get(Location.class)).thenReturn(mockLocation);

        mockedOBDal.when(OBDal::getInstance).thenReturn(mock(OBDal.class));
        when(OBDal.getInstance().createCriteria(Country.class)).thenReturn(mockCountryCriteria);
        when(mockCountryCriteria.uniqueResult()).thenReturn(mockCountry);

        Map<String, String> responseVars = new HashMap<>();
        locationCreatorWebhook.get(parameters, responseVars);

        // Then
        verify(mockLocation).setAddressLine1("123 Test Street");
        verify(mockLocation).setCityName("Test City");
        verify(mockLocation).setPostalCode("12345");
        verify(mockLocation).setCountry(mockCountry);
        verify(OBDal.getInstance()).save(mockLocation);
        verify(OBDal.getInstance()).flush();

        assertTrue(responseVars.containsKey("message"));
        assertEquals("Location processed successfully: null", responseVars.get("message"));
    }

    @Test
    public void testCreateLocationMissingParameters() {
        // Given
        Map<String, String> parameters = new HashMap<>();
        parameters.put("Address1", "123 Test Street");
        // Missing other required parameters

        Map<String, String> responseVars = new HashMap<>();

        // When
        locationCreatorWebhook.get(parameters, responseVars);

        // Then
        assertTrue(responseVars.containsKey("error"));
        assertTrue(responseVars.get("error").contains("Missing parameter"));
    }

    @Test
    public void testCreateLocationCountryNotFound() {
        // Given
        Map<String, String> parameters = new HashMap<>();
        parameters.put("Address1", "123 Test Street");
        parameters.put("City", "Test City");
        parameters.put("Postal", "12345");
        parameters.put("CountryISOCode", "INVALID");

        Location mockLocation = mock(Location.class);
        OBProvider mockProvider = mock(OBProvider.class);
        OBCriteria<Country> mockCountryCriteria = mock(OBCriteria.class);

        // When
        mockedOBProvider.when(() -> OBProvider.getInstance()).thenReturn(mockProvider);
        when(mockProvider.get(Location.class)).thenReturn(mockLocation);

        mockedOBDal.when(OBDal::getInstance).thenReturn(mock(OBDal.class));
        when(OBDal.getInstance().createCriteria(Country.class)).thenReturn(mockCountryCriteria);
        when(mockCountryCriteria.uniqueResult()).thenReturn(null);
        when(mockCountryCriteria.list()).thenReturn(java.util.Collections.emptyList());

        Map<String, String> responseVars = new HashMap<>();
        locationCreatorWebhook.get(parameters, responseVars);

        // Then
        assertTrue(responseVars.containsKey("error"));
        assertEquals("Country not found", responseVars.get("error"));
        assertTrue(responseVars.containsKey("countries"));
    }

    @Test
    public void testUpdateLocationSuccess() {
        // Given
        Map<String, String> parameters = new HashMap<>();
        parameters.put("ID", "testLocationId");
        parameters.put("Address1", "456 Updated Street");
        parameters.put("City", "Updated City");
        parameters.put("Postal", "54321");
        parameters.put("CountryISOCode", "CA");

        Location mockLocation = mock(Location.class);
        Country mockCountry = mock(Country.class);
        OBCriteria<Country> mockCountryCriteria = mock(OBCriteria.class);

        // When
        mockedOBDal.when(OBDal::getInstance).thenReturn(mock(OBDal.class));
        when(OBDal.getInstance().get(Location.class, "testLocationId")).thenReturn(mockLocation);
        when(OBDal.getInstance().createCriteria(Country.class)).thenReturn(mockCountryCriteria);
        when(mockCountryCriteria.uniqueResult()).thenReturn(mockCountry);

        Map<String, String> responseVars = new HashMap<>();
        locationCreatorWebhook.get(parameters, responseVars);

        // Then
        verify(mockLocation).setAddressLine1("456 Updated Street");
        verify(mockLocation).setCityName("Updated City");
        verify(mockLocation).setPostalCode("54321");
        verify(mockLocation).setCountry(mockCountry);
        verify(OBDal.getInstance()).save(mockLocation);
        verify(OBDal.getInstance()).flush();

        assertTrue(responseVars.containsKey("message"));
        assertEquals("Location processed successfully: null", responseVars.get("message"));
    }
}