/*
 * Copyright (c) 2023, WSO2 LLC (http://www.wso2.com).
 *
 * WSO2 LLC licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.am.integration.tests.other;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.wso2.am.integration.clients.publisher.api.v1.dto.APIOperationsDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationKeyDTO;
import org.wso2.am.integration.clients.store.api.v1.dto.ApplicationKeyGenerateRequestDTO;
import org.wso2.am.integration.test.impl.RestAPIPublisherImpl;
import org.wso2.am.integration.test.impl.RestAPIStoreImpl;
import org.wso2.am.integration.test.utils.base.APIMIntegrationBaseTest;
import org.wso2.am.integration.test.utils.base.APIMIntegrationConstants;
import org.wso2.am.integration.test.utils.bean.APILifeCycleAction;
import org.wso2.am.integration.test.utils.bean.APIRequest;
import org.wso2.carbon.automation.test.utils.http.client.HttpRequestUtil;
import org.wso2.carbon.automation.test.utils.http.client.HttpResponse;
import org.wso2.carbon.tenant.mgt.stub.TenantMgtAdminServiceExceptionException;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;

public class TenantDomainValidationTestCase extends APIMIntegrationBaseTest {

    private final String TENANT_DOMAIN = "abc.com";
    private final String TENANT_ADMIN_USERNAME = "admin";
    private final String TENANT_ADMIN_PASSWORD = "password1";
    private final String API_NAME = "ABC_API";
    private final String API_VERSION = "1.0.0";
    private final String API_DESC = "This is a test API Created by API Manager Integration Test";
    private final String APP_NAME = "TenantABCApp";
    private final String TENANT_ADMIN_USER = TENANT_ADMIN_USERNAME + "@" + TENANT_DOMAIN;
    private final String API_CONTEXT = "testABC_API";
    private final String INVALID_TENANT_DOMAIN = "Abc.com";
    private final String API_END_POINT_POSTFIX_URL = "jaxrs_basic/services/customers/customerservice/";
    private String apiProductionEndPointUrl;
    private String apiID;
    private String appID;

    @BeforeClass(alwaysRun = true)
    public void setEnvironment() throws Exception {

        super.init();
        apiProductionEndPointUrl = backEndServerUrl.getWebAppURLHttp() +
                API_END_POINT_POSTFIX_URL;
    }

    @Test(groups = {"wso2.am"}, description = "Testing adding a tenant with invalid domain")
    public void testAdditionOfTenantWithInvalidDomain() throws Exception {

        try {
            tenantManagementServiceClient.addTenant(INVALID_TENANT_DOMAIN, TENANT_ADMIN_PASSWORD, TENANT_ADMIN_USERNAME,
                    "demo");
        } catch (TenantMgtAdminServiceExceptionException e) {
            assertEquals(e.getFaultMessage().getTenantMgtAdminServiceException().getMessage(),
                    "The tenant domain ' " + INVALID_TENANT_DOMAIN + " ' contains one or more illegal " +
                            "characters. The valid characters are lowercase letters, numbers, '.', '-' and '_'.");
        }
    }

    @Test(groups = {
            "wso2.am"}, description = "Testing API invocation with a different tenant domain")
    public void testAPIInvokeWithTenants() throws Exception {

        // Add a new tenant
        tenantManagementServiceClient.addTenant(TENANT_DOMAIN, TENANT_ADMIN_PASSWORD, TENANT_ADMIN_USERNAME, "demo");

        restAPIPublisher = new RestAPIPublisherImpl(TENANT_ADMIN_USERNAME, TENANT_ADMIN_PASSWORD, TENANT_DOMAIN,
                publisherURLHttps);

        restAPIStore = new RestAPIStoreImpl(TENANT_ADMIN_USERNAME, TENANT_ADMIN_PASSWORD,
                TENANT_DOMAIN, storeURLHttps);

        Thread.sleep(15000);

        APIRequest apiCreationRequestBean;
        apiCreationRequestBean = new APIRequest(API_NAME, API_CONTEXT, new URL(apiProductionEndPointUrl));
        apiCreationRequestBean.setVersion(API_VERSION);
        apiCreationRequestBean.setDescription(API_DESC);
        apiCreationRequestBean.setProvider(TENANT_ADMIN_USER);
        apiCreationRequestBean.setTier(APIMIntegrationConstants.APPLICATION_TIER.UNLIMITED);
        APIOperationsDTO apiOperationsDTO = new APIOperationsDTO();
        apiOperationsDTO.setVerb("GET");
        apiOperationsDTO.setTarget("/customers/{id}");
        List<APIOperationsDTO> operationsDTOS = new ArrayList<>();
        operationsDTOS.add(apiOperationsDTO);
        apiCreationRequestBean.setOperationsDTOS(operationsDTOS);
        apiCreationRequestBean.setOperationsDTOS(operationsDTOS);

        HttpResponse apiCreationResponse = restAPIPublisher.addAPI(apiCreationRequestBean);
        apiID = apiCreationResponse.getData();

        restAPIPublisher
                .changeAPILifeCycleStatus(apiID, APILifeCycleAction.PUBLISH.getAction(), null);

        HttpResponse applicationResponse = restAPIStore
                .createApplication(APP_NAME, "Application to test Schema "
                                + "Validation", APIMIntegrationConstants.APPLICATION_TIER.UNLIMITED,
                        ApplicationDTO.TokenTypeEnum.JWT);
        ArrayList<String> grantTypes = new ArrayList<>();
        grantTypes.add(APIMIntegrationConstants.GRANT_TYPE.PASSWORD);
        grantTypes.add(APIMIntegrationConstants.GRANT_TYPE.CLIENT_CREDENTIAL);
        appID = applicationResponse.getData();
        restAPIStore.subscribeToAPI(apiID, appID, "Gold");

        ApplicationKeyDTO applicationKeyDTO = restAPIStore.generateKeys(appID, "36000", "",
                ApplicationKeyGenerateRequestDTO.KeyTypeEnum.PRODUCTION, null, grantTypes);
        String accessToken = applicationKeyDTO.getToken().getAccessToken();

        // Invoke the API with a valid tenant domain
        String gatewayUrl = gatewayUrlsWrk.getWebAppURLNhttp() + "t/" + TENANT_DOMAIN + "/";
        HttpResponse response = invokeAPI(accessToken, gatewayUrl);

        assertEquals(response.getResponseCode(), 200,
                "API Invocation failed with valid tenant : " + TENANT_ADMIN_USER);

        // Invoke the API with a invalid tenant domain
        gatewayUrl = gatewayUrlsWrk.getWebAppURLNhttp() + "t/" + INVALID_TENANT_DOMAIN + "/";
        response = invokeAPI(accessToken, gatewayUrl);

        assertEquals(response.getResponseCode(), 500,
                "Expected response code 500 but received " + response.getResponseCode() + " when invoking API with " +
                        "invalid tenant domain");

        // Invoke the API with a valid tenant domain again to check nothing have breaked
        gatewayUrl = gatewayUrlsWrk.getWebAppURLNhttp() + "t/" + TENANT_DOMAIN + "/";
        response = invokeAPI(accessToken, gatewayUrl);

        assertEquals(response.getResponseCode(), 200,
                "API Invocation failed with valid tenant : " + TENANT_ADMIN_USER + " after a request with invalid " +
                        "tenant domain");
    }

    private HttpResponse invokeAPI(String accessToken, String gatewayUrl)
            throws Exception {

        Map<String, String> requestHeaders = new HashMap<>();
        String endPointURL = gatewayUrl + API_CONTEXT + "/" + API_VERSION + "/customers/123";
        requestHeaders.put("Authorization", "Bearer " + accessToken);
        requestHeaders.put("accept", "application/json");

        // Accessing GET method
        return HttpRequestUtil.doGet(endPointURL, requestHeaders);
    }

    @AfterClass(alwaysRun = true)
    public void destroy() throws Exception {

        restAPIStore.deleteApplication(appID);
        restAPIPublisher.deleteAPI(apiID);
        tenantManagementServiceClient.deleteTenant(TENANT_DOMAIN);
        super.cleanUp();
    }
}
