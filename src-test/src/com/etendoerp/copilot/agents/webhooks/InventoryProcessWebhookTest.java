package com.etendoerp.copilot.agents.webhooks;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.materialmgmt.InventoryCountProcess;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.materialmgmt.transaction.InventoryCount;

import org.openbravo.test.base.TestConstants;

public class InventoryProcessWebhookTest extends WeldBaseTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private InventoryProcessWebhook inventoryProcessWebhook;

    @Mock
    private InventoryCount mockInventoryCount;

    @Mock
    private OBContext obContext;

    @Mock
    private InventoryCountProcess mockInventoryCountProcess;

    private MockedStatic<OBDal> mockedOBDal;
    private MockedStatic<OBContext> mockedOBContext;
    private MockedStatic<OBMessageUtils> mockedOBMessageUtils;

    @Before
    public void setUp() throws Exception {
        // Initialize mocks
        MockitoAnnotations.openMocks(this);

        // Initialize the webhook
        inventoryProcessWebhook = new InventoryProcessWebhook();

        // Mock static methods
        mockedOBDal = mockStatic(OBDal.class);
        mockedOBContext = mockStatic(OBContext.class);
        mockedOBMessageUtils = mockStatic(OBMessageUtils.class);

        mockedOBContext.when(OBContext::getOBContext).thenReturn(obContext);
        mockedOBMessageUtils.when(() -> OBMessageUtils.messageBD("Success"))
                .thenReturn("Success");

        // Set OBContext
        OBContext.setOBContext(TestConstants.Users.ADMIN, TestConstants.Roles.FB_GRP_ADMIN,
                TestConstants.Clients.FB_GRP, TestConstants.Orgs.ESP_NORTE);

        // Mock OBContext.getLanguage() to return a valid Language object
        Language mockLanguage = mock(Language.class);
        when(mockLanguage.getId()).thenReturn("en_US");
        when(obContext.getLanguage()).thenReturn(mockLanguage);
    }

    @After
    public void tearDown() {
        // Close static mocks
        if (mockedOBDal != null) {
            mockedOBDal.close();
        }
        if (mockedOBContext != null) {
            mockedOBContext.close();
        }
        if (mockedOBMessageUtils != null) {
            mockedOBMessageUtils.close();
        }
    }

    @Test
    public void testSuccessfulInventoryProcess() {
        // Given
        Map<String, String> parameters = new HashMap<>();
        parameters.put("inventory_id", "testInventoryId");
        Map<String, String> responseVars = new HashMap<>();

        // Mock OBDal to return a mock InventoryCount
        OBDal mockOBDal = mock(OBDal.class);
        mockedOBDal.when(OBDal::getInstance).thenReturn(mockOBDal);
        when(mockOBDal.get(InventoryCount.class, "testInventoryId")).thenReturn(mockInventoryCount);

        // Create a mock OBError for successful processing
        OBError successOBError = new OBError();
        successOBError.setType("Success");

        // Use MockedConstruction to intercept the constructor and configure the instance
        try (MockedConstruction<InventoryCountProcess> mocked = Mockito.mockConstruction(
                InventoryCountProcess.class,
                (mock, context) -> {
                    when(mock.processInventory(any(InventoryCount.class), eq(false), eq(true)))
                            .thenReturn(successOBError);
                })) {

            // When
            inventoryProcessWebhook.get(parameters, responseVars);

            // Then
            assertTrue(responseVars.containsKey("message"));
            assertEquals("Inventory processed successfully.", responseVars.get("message"));
        }
    }

    @Test
    public void testMissingInventoryId() {
        // Given
        Map<String, String> parameters = new HashMap<>();
        Map<String, String> responseVars = new HashMap<>();

        // When
        inventoryProcessWebhook.get(parameters, responseVars);

        // Then
        assertTrue(responseVars.containsKey("error"));
        assertEquals("Missing parameter: inventory_id", responseVars.get("error"));
    }

    @Test
    public void testNonExistentInventory() {
        // Given
        Map<String, String> parameters = new HashMap<>();
        parameters.put("inventory_id", "nonExistentId");
        Map<String, String> responseVars = new HashMap<>();

        // Mock OBDal to return null for non-existent inventory
        OBDal mockOBDal = mock(OBDal.class);
        mockedOBDal.when(OBDal::getInstance).thenReturn(mockOBDal);
        when(mockOBDal.get(InventoryCount.class, "nonExistentId")).thenReturn(null);

        // When
        inventoryProcessWebhook.get(parameters, responseVars);

        // Then
        assertTrue(responseVars.containsKey("error"));
        assertEquals("Requested Inventory does not exist", responseVars.get("error"));
    }

    @Test
    public void testInventoryProcessFailure() {
        // Given
        Map<String, String> parameters = new HashMap<>();
        parameters.put("inventory_id", "testInventoryId");
        Map<String, String> responseVars = new HashMap<>();

        // Mock OBDal to return a mock InventoryCount
        OBDal mockOBDal = mock(OBDal.class);
        mockedOBDal.when(OBDal::getInstance).thenReturn(mockOBDal);
        when(mockOBDal.get(InventoryCount.class, "testInventoryId")).thenReturn(mockInventoryCount);

        // Create a mock OBError for failed processing
        OBError errorOBError = new OBError();
        errorOBError.setType("Error");
        errorOBError.setMessage("Processing error");

        // Use MockedConstruction to intercept the constructor and configure the instance
        try (MockedConstruction<InventoryCountProcess> mocked = Mockito.mockConstruction(
                InventoryCountProcess.class,
                (mock, context) -> {
                    when(mock.processInventory(any(InventoryCount.class), eq(false), eq(true)))
                            .thenReturn(errorOBError);
                })) {

            // When
            inventoryProcessWebhook.get(parameters, responseVars);

            // Then
            assertTrue(responseVars.containsKey("error"));
            assertEquals("Failed to process Inventory: Processing error", responseVars.get("error"));
        }
    }
}