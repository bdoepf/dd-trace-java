import datadog.opentracing.DDSpan
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.api.DDSpanTypes
import datadog.trace.api.DDTags
import io.opentracing.tag.Tags
import org.apache.http.HttpResponse
import org.apache.http.client.ClientProtocolException
import org.apache.http.client.HttpClient
import org.apache.http.client.config.RequestConfig
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.message.BasicHeader
import spock.lang.AutoCleanup
import spock.lang.Shared

import static datadog.trace.agent.test.asserts.ListWriterAssert.assertTraces
import static datadog.trace.agent.test.server.http.TestHttpServer.httpServer

class ApacheHttpClientTest extends AgentTestRunner {

  @AutoCleanup
  @Shared
  def server = httpServer {
    handlers {
      prefix("success") {
        handleDistributedRequest()
        String msg = "<html><body><h1>Hello test.</h1>\n"
        response.status(200).send(msg)
      }
      prefix("redirect") {
        handleDistributedRequest()
        redirect(server.address.resolve("/success").toURL().toString())
      }
      prefix("another-redirect") {
        handleDistributedRequest()
        redirect(server.address.resolve("/redirect").toURL().toString())
      }
    }
  }
  @Shared
  int port = server.address.port
  @Shared
  def successUrl = server.address.resolve("/success")
  @Shared
  def redirectUrl = server.address.resolve("/redirect")
  @Shared
  def twoRedirectsUrl = server.address.resolve("/another-redirect")

  final HttpClientBuilder builder = HttpClientBuilder.create()
  final HttpClient client = builder.build()

  def "trace request with propagation"() {
    when:
    HttpResponse response = client.execute(new HttpGet(successUrl))

    then:
    response.getStatusLine().getStatusCode() == 200
    // one trace on the server, one trace on the client
    assertTraces(TEST_WRITER, 2) {
      server.distributedRequestTrace(it, 0, TEST_WRITER[1][1])
      trace(1, 2) {
        clientParentSpan(it, 0)
        successClientSpan(it, 1, span(0))
      }
    }
  }

  def "trace redirected request with propagation many redirects allowed"() {
    setup:
    final RequestConfig.Builder requestConfigBuilder = new RequestConfig.Builder()
    requestConfigBuilder.setMaxRedirects(10)

    HttpGet request = new HttpGet(redirectUrl)
    request.setConfig(requestConfigBuilder.build())

    when:
    HttpResponse response = client.execute(request)

    then:
    response.getStatusLine().getStatusCode() == 200
    // two traces on the server, one trace on the client
    assertTraces(TEST_WRITER, 3) {
      server.distributedRequestTrace(it, 0, TEST_WRITER[2][2])
      server.distributedRequestTrace(it, 1, TEST_WRITER[2][1])
      trace(2, 3) {
        clientParentSpan(it, 0)
        successClientSpan(it, 1, span(0))
        redirectClientSpan(it, 2, span(0))
      }
    }
  }

  def "trace redirected request with propagation 1 redirect allowed"() {
    setup:
    final RequestConfig.Builder requestConfigBuilder = new RequestConfig.Builder()
    requestConfigBuilder.setMaxRedirects(1)
    HttpGet request = new HttpGet(redirectUrl)
    request.setConfig(requestConfigBuilder.build())

    when:
    HttpResponse response = client.execute(request)

    then:
    response.getStatusLine().getStatusCode() == 200
    // two traces on the server, one trace on the client
    assertTraces(TEST_WRITER, 3) {
      server.distributedRequestTrace(it, 0, TEST_WRITER[2][2])
      server.distributedRequestTrace(it, 1, TEST_WRITER[2][1])
      trace(2, 3) {
        clientParentSpan(it, 0)
        successClientSpan(it, 1, span(0))
        redirectClientSpan(it, 2, span(0))
      }
    }
  }

  def "trace redirected request with propagation too many redirects"() {
    setup:
    final RequestConfig.Builder requestConfigBuilder = new RequestConfig.Builder()
    requestConfigBuilder.setMaxRedirects(1)

    HttpGet request = new HttpGet(twoRedirectsUrl)
    request.setConfig(requestConfigBuilder.build())

    when:
    client.execute(request)

    then:
    def exception = thrown(ClientProtocolException)
    // two traces on the server, one trace on the client
    assertTraces(TEST_WRITER, 3) {
      server.distributedRequestTrace(it, 0, TEST_WRITER[2][2])
      server.distributedRequestTrace(it, 1, TEST_WRITER[2][1])
      trace(2, 3) {
        clientParentSpan(it, 0, exception)
        redirectClientSpan(it, 1, span(0))
        redirectClientSpan(it, 2, span(0), "another-redirect")
      }
    }
  }

  def "trace request without propagation"() {
    setup:
    HttpGet request = new HttpGet(successUrl)
    request.addHeader(new BasicHeader("is-dd-server", "false"))

    when:
    HttpResponse response = client.execute(request)

    then:
    response.getStatusLine().getStatusCode() == 200
    // only one trace (client).
    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        clientParentSpan(it, 0)
        successClientSpan(it, 1, span(0))
      }
    }
  }

  def clientParentSpan(TraceAssert trace, int index, Throwable exception = null) {
    trace.span(index) {
      parent()
      serviceName "unnamed-java-app"
      operationName "apache.http"
      resourceName "apache.http"
      errored exception != null
      tags {
        defaultTags()
        "$Tags.COMPONENT.key" "apache-httpclient"
        if (exception) {
          errorTags(exception.class)
        }
      }
    }
  }

  def successClientSpan(TraceAssert trace, int index, DDSpan parent, status = 200, route = "success") {
    trace.span(index) {
      childOf parent
      serviceName "unnamed-java-app"
      operationName "http.request"
      resourceName "GET /$route"
      errored false
      tags {
        defaultTags()
        "$Tags.HTTP_STATUS.key" status
        "$Tags.HTTP_URL.key" "http://localhost:$port/$route"
        "$Tags.PEER_HOSTNAME.key" "localhost"
        "$Tags.PEER_PORT.key" server.getAddress().port
        "$Tags.HTTP_METHOD.key" "GET"
        "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_CLIENT
        "$DDTags.SPAN_TYPE" DDSpanTypes.HTTP_CLIENT
      }
    }
  }

  def redirectClientSpan(TraceAssert trace, int index, DDSpan parent, route = "redirect") {
    successClientSpan(trace, index, parent, 302, route)
  }
}
