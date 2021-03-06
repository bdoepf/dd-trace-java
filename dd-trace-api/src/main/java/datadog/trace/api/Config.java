package datadog.trace.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Config gives priority to system properties and falls back to environment variables. It also
 * includes default values to ensure a valid config.
 *
 * <p>
 *
 * <p>System properties are {@link Config#PREFIX}'ed. Environment variables are the same as the
 * system property, but uppercased with '.' -> '_'.
 */
@Slf4j
@ToString(includeFieldNames = true)
public class Config {
  /** Config keys below */
  private static final String PREFIX = "dd.";

  private static final Config INSTANCE = new Config();

  public static final String SERVICE_NAME = "service.name";
  public static final String SERVICE_MAPPING = "service.mapping";
  public static final String WRITER_TYPE = "writer.type";
  public static final String AGENT_HOST = "agent.host";
  public static final String AGENT_PORT = "agent.port";
  public static final String PRIORITY_SAMPLING = "priority.sampling";
  public static final String TRACE_RESOLVER_ENABLED = "trace.resolver.enabled";
  public static final String SPAN_TAGS = "trace.span.tags";
  public static final String HEADER_TAGS = "trace.header.tags";

  public static final String DEFAULT_SERVICE_NAME = "unnamed-java-app";

  public static final String DD_AGENT_WRITER_TYPE = "DDAgentWriter";
  public static final String LOGGING_WRITER_TYPE = "LoggingWriter";
  public static final String DEFAULT_AGENT_WRITER_TYPE = DD_AGENT_WRITER_TYPE;

  public static final String DEFAULT_AGENT_HOST = "localhost";
  public static final int DEFAULT_AGENT_PORT = 8126;

  private static final String DEFAULT_PRIORITY_SAMPLING_ENABLED = "false";
  private static final String DEFAULT_TRACE_RESOLVER_ENABLED = "true";

  @Getter private final String serviceName;
  @Getter private final String writerType;
  @Getter private final String agentHost;
  @Getter private final int agentPort;
  @Getter private final boolean prioritySamplingEnabled;
  @Getter private final boolean traceResolverEnabled;
  @Getter private final Map<String, String> serviceMapping;
  @Getter private final Map<String, String> spanTags;
  @Getter private final Map<String, String> headerTags;

  // Read order: System Properties -> Env Variables, [-> default value]
  // Visible for testing
  Config() {
    serviceName = getSettingFromEnvironment(SERVICE_NAME, DEFAULT_SERVICE_NAME);
    writerType = getSettingFromEnvironment(WRITER_TYPE, DEFAULT_AGENT_WRITER_TYPE);
    agentHost = getSettingFromEnvironment(AGENT_HOST, DEFAULT_AGENT_HOST);
    agentPort =
        Integer.valueOf(
            getSettingFromEnvironment(AGENT_PORT, Integer.toString(DEFAULT_AGENT_PORT)));
    prioritySamplingEnabled =
        Boolean.valueOf(
            getSettingFromEnvironment(PRIORITY_SAMPLING, DEFAULT_PRIORITY_SAMPLING_ENABLED));
    traceResolverEnabled =
        Boolean.valueOf(
            getSettingFromEnvironment(TRACE_RESOLVER_ENABLED, DEFAULT_TRACE_RESOLVER_ENABLED));
    serviceMapping = getMapSettingFromEnvironment(SERVICE_MAPPING, null);
    spanTags = getMapSettingFromEnvironment(SPAN_TAGS, null);
    headerTags = getMapSettingFromEnvironment(HEADER_TAGS, null);
  }

  // Read order: Properties -> Parent
  private Config(final Properties properties, final Config parent) {
    serviceName = properties.getProperty(SERVICE_NAME, parent.serviceName);
    writerType = properties.getProperty(WRITER_TYPE, parent.writerType);
    agentHost = properties.getProperty(AGENT_HOST, parent.agentHost);
    agentPort =
        Integer.valueOf(properties.getProperty(AGENT_PORT, Integer.toString(parent.agentPort)));
    prioritySamplingEnabled =
        Boolean.valueOf(
            properties.getProperty(
                PRIORITY_SAMPLING, Boolean.toString(parent.prioritySamplingEnabled)));
    traceResolverEnabled =
        Boolean.valueOf(
            properties.getProperty(
                TRACE_RESOLVER_ENABLED, Boolean.toString(parent.traceResolverEnabled)));
    serviceMapping = getPropertyMapValue(properties, SERVICE_MAPPING, parent.serviceMapping);
    spanTags = getPropertyMapValue(properties, SPAN_TAGS, parent.spanTags);
    headerTags = getPropertyMapValue(properties, HEADER_TAGS, parent.headerTags);
  }

  private static String getSettingFromEnvironment(final String name, final String defaultValue) {
    final String completeName = PREFIX + name;
    final String value =
        System.getProperties()
            .getProperty(completeName, System.getenv(propertyToEnvironmentName(completeName)));
    return value == null ? defaultValue : value;
  }

  private static Map<String, String> getMapSettingFromEnvironment(
      final String name, final String defaultValue) {
    return parseMap(getSettingFromEnvironment(name, defaultValue), PREFIX + name);
  }

  private static String propertyToEnvironmentName(final String name) {
    return name.toUpperCase().replace(".", "_");
  }

  private static Map<String, String> getPropertyMapValue(
      final Properties properties, final String name, final Map<String, String> defaultValue) {
    final String value = properties.getProperty(name);
    return value == null ? defaultValue : parseMap(value, name);
  }

  private static Map<String, String> parseMap(final String str, final String settingName) {
    if (str == null || str.trim().isEmpty()) {
      return Collections.emptyMap();
    }
    if (!str.matches("(([^,:]+:[^,:]*,)*([^,:]+:[^,:]*),?)?")) {
      log.warn(
          "Invalid config for {}: '{}'. Must match 'key1:value1,key2:value2'.", settingName, str);
      return Collections.emptyMap();
    }

    final String[] tokens = str.split(",", -1);
    final Map<String, String> map = new HashMap<>(tokens.length + 1, 1f);

    for (final String token : tokens) {
      final String[] keyValue = token.split(":", -1);
      if (keyValue.length == 2) {
        final String key = keyValue[0].trim();
        final String value = keyValue[1].trim();
        if (value.length() <= 0) {
          log.warn("Ignoring empty value for key '{}' in config for {}", key, settingName);
          continue;
        }
        map.put(key, value);
      }
    }
    return Collections.unmodifiableMap(map);
  }

  public static Config get() {
    return INSTANCE;
  }

  public static Config get(final Properties properties) {
    if (properties == null || properties.isEmpty()) {
      return INSTANCE;
    } else {
      return new Config(properties, INSTANCE);
    }
  }
}
