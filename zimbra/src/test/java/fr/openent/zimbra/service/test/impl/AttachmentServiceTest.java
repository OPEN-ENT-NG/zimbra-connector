package fr.openent.zimbra.service.test.impl;

import fr.openent.zimbra.Zimbra;
import fr.openent.zimbra.core.constants.Field;
import fr.openent.zimbra.helper.ConfigManager;
import fr.openent.zimbra.helper.HttpClientHelper;
import fr.openent.zimbra.service.data.SoapZimbraService;
import fr.openent.zimbra.service.impl.AttachmentService;
import fr.openent.zimbra.service.impl.MessageService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.entcore.common.user.UserInfos;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(VertxUnitRunner.class)
@PrepareForTest({HttpClientHelper.class, Zimbra.class})
public class AttachmentServiceTest {

    private Vertx vertx;
    private AttachmentService attachmentService;
    private SoapZimbraService soapZimbraService;
    private MessageService messageService;
    private HttpClient httpClient;

    @Before
    public void setUp() throws IllegalAccessException {
        this.vertx = Mockito.spy(Vertx.vertx());
        this.soapZimbraService = mock(SoapZimbraService.class);
        this.messageService = mock(MessageService.class);
        this.attachmentService = new AttachmentService(soapZimbraService, messageService, Vertx.vertx(), new JsonObject(), null);

        this.httpClient = Mockito.mock(HttpClient.class);
        mockStatic(HttpClientHelper.class);
        PowerMockito.when(HttpClientHelper.createHttpClient(Mockito.any())).thenReturn(this.httpClient);

        ConfigManager configManager = mock(ConfigManager.class);
        mockStatic(Zimbra.class);
        PowerMockito.field(Zimbra.class, "appConfig").set(Zimbra.class, configManager);
        PowerMockito.when(Zimbra.appConfig.getZimbraFileUploadMaxSize()).thenReturn(20);
    }

    @Test
    public void testAddAttachmentBuffer_too_big(TestContext ctx) {
        Async async = ctx.async();

        String messageId = "1";
        UserInfos userInfos = new UserInfos();

        HttpServerRequest httpServerRequest = mock(HttpServerRequest.class);
        HttpClientRequest httpClientRequest = mock(HttpClientRequest.class);
        HttpClientResponse httpClientResponse = mock(HttpClientResponse.class);
        Buffer buffer = mock(Buffer.class);
        Mockito.when(buffer.toString()).thenReturn("413,null\n");

        Mockito.doAnswer(invocation -> {
            Handler<Either<String, JsonObject>> response = invocation.getArgument(1);
            response.handle(new Either.Right<>(new JsonObject().put(Field.AUTH_TOKEN, "token")));
            return null;
        }).when(soapZimbraService).getUserAuthToken(Mockito.any(), Mockito.any());

        Mockito.doReturn(200).when(httpClientResponse).statusCode();
        Mockito.doAnswer(invocation -> {
            Handler<HttpClientResponse> response = invocation.getArgument(1);
            response.handle(httpClientResponse);
            return httpClientRequest;
        }).when(httpClient).postAbs(Mockito.anyString(), Mockito.any());
        Mockito.doAnswer(invocation -> httpClientRequest).when(httpClientRequest).setChunked(true);
        Mockito.doAnswer(invocation -> httpClientRequest).when(httpClientRequest).putHeader(Mockito.anyString(), Mockito.anyString());

        Mockito.doAnswer(invocation -> {
            Handler<Buffer> bodyHandler = invocation.getArgument(0);
            bodyHandler.handle(buffer);
            return null;
        }).when(httpClientResponse).bodyHandler(Mockito.any());

        this.attachmentService.addAttachmentBuffer(messageId, userInfos, httpServerRequest, result -> {
            String expected = "{\"code\":\"mail.ATTACHMENT_TOO_BIG\",\"maxFileSize\":20}";
            ctx.assertEquals(expected, result.left().getValue());
            async.complete();
        });

        async.awaitSuccess(10000);
    }

    @Test
    public void testAddAttachmentBuffer_invalid_request(TestContext ctx) {
        Async async = ctx.async();

        String messageId = "1";
        UserInfos userInfos = new UserInfos();

        HttpServerRequest httpServerRequest = mock(HttpServerRequest.class);
        HttpClientRequest httpClientRequest = mock(HttpClientRequest.class);
        HttpClientResponse httpClientResponse = mock(HttpClientResponse.class);
        Buffer buffer = mock(Buffer.class);
        Mockito.when(buffer.toString()).thenReturn("400,null\n");

        Mockito.doAnswer(invocation -> {
            Handler<Either<String, JsonObject>> response = invocation.getArgument(1);
            response.handle(new Either.Right<>(new JsonObject().put(Field.AUTH_TOKEN, "token")));
            return null;
        }).when(soapZimbraService).getUserAuthToken(Mockito.any(), Mockito.any());

        Mockito.doReturn(200).when(httpClientResponse).statusCode();
        Mockito.doAnswer(invocation -> {
            Handler<HttpClientResponse> response = invocation.getArgument(1);
            response.handle(httpClientResponse);
            return httpClientRequest;
        }).when(httpClient).postAbs(Mockito.anyString(), Mockito.any());
        Mockito.doAnswer(invocation -> httpClientRequest).when(httpClientRequest).setChunked(true);
        Mockito.doAnswer(invocation -> httpClientRequest).when(httpClientRequest).putHeader(Mockito.anyString(), Mockito.anyString());

        Mockito.doAnswer(invocation -> {
            Handler<Buffer> bodyHandler = invocation.getArgument(0);
            bodyHandler.handle(buffer);
            return null;
        }).when(httpClientResponse).bodyHandler(Mockito.any());

        this.attachmentService.addAttachmentBuffer(messageId, userInfos, httpServerRequest, result -> {
            String expected = "{\"code\":\"mail.INVALID_REQUEST\"}";
            ctx.assertEquals(expected, result.left().getValue());
            async.complete();
        });

        async.awaitSuccess(10000);
    }
}
