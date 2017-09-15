/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache license, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the license for the specific language governing permissions and
 * limitations under the license.
 */
package org.apache.logging.log4j.core.layout;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.impl.MutableLogEvent;
import org.apache.logging.log4j.core.jackson.XmlConstants;
import org.apache.logging.log4j.core.lookup.StrSubstitutor;
import org.apache.logging.log4j.core.util.KeyValuePair;
import org.apache.logging.log4j.core.util.StringBuilderWriter;
import org.apache.logging.log4j.util.Strings;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

abstract class AbstractJacksonLayout extends AbstractStringLayout {

    protected static final String DEFAULT_EOL = "\r\n";
    protected static final String COMPACT_EOL = Strings.EMPTY;

    public static abstract class Builder<B extends Builder<B>> extends AbstractStringLayout.Builder<B> {

        @PluginBuilderAttribute
        private boolean eventEol;

        @PluginBuilderAttribute
        private boolean compact;

        @PluginBuilderAttribute
        private boolean complete;

        @PluginBuilderAttribute
        private boolean locationInfo;

        @PluginBuilderAttribute
        private boolean properties;

        @PluginBuilderAttribute
        private boolean includeStacktrace = true;

        @PluginBuilderAttribute
        private boolean stacktraceAsString = false;

        @PluginBuilderAttribute
        private boolean includeNullDelimiter = false;

        @PluginElement("AdditionalField")
        private KeyValuePair[] additionalFields;

        protected String toStringOrNull(final byte[] header) {
            return header == null ? null : new String(header, Charset.defaultCharset());
        }

        public boolean getEventEol() {
            return eventEol;
        }

        public boolean isCompact() {
            return compact;
        }

        public boolean isComplete() {
            return complete;
        }

        public boolean isLocationInfo() {
            return locationInfo;
        }

        public boolean isProperties() {
            return properties;
        }

        /**
         * If "true", includes the stacktrace of any Throwable in the generated data, defaults to "true".
         * @return If "true", includes the stacktrace of any Throwable in the generated data, defaults to "true".
         */
        public boolean isIncludeStacktrace() {
            return includeStacktrace;
        }

        public boolean isStacktraceAsString() {
            return stacktraceAsString;
        }

        public boolean isIncludeNullDelimiter() { return includeNullDelimiter; }

        public KeyValuePair[] getAdditionalFields() {
            return additionalFields;
        }

        public B setEventEol(final boolean eventEol) {
            this.eventEol = eventEol;
            return asBuilder();
        }

        public B setCompact(final boolean compact) {
            this.compact = compact;
            return asBuilder();
        }

        public B setComplete(final boolean complete) {
            this.complete = complete;
            return asBuilder();
        }

        public B setLocationInfo(final boolean locationInfo) {
            this.locationInfo = locationInfo;
            return asBuilder();
        }

        public B setProperties(final boolean properties) {
            this.properties = properties;
            return asBuilder();
        }

        /**
         * If "true", includes the stacktrace of any Throwable in the generated JSON, defaults to "true".
         * @param includeStacktrace If "true", includes the stacktrace of any Throwable in the generated JSON, defaults to "true".
         * @return this builder
         */
        public B setIncludeStacktrace(final boolean includeStacktrace) {
            this.includeStacktrace = includeStacktrace;
            return asBuilder();
        }

        /**
         * Whether to format the stacktrace as a string, and not a nested object (optional, defaults to false).
         *
         * @return this builder
         */
        public B setStacktraceAsString(final boolean stacktraceAsString) {
            this.stacktraceAsString = stacktraceAsString;
            return asBuilder();
        }

        /**
         * Whether to include NULL byte as delimiter after each event (optional, default to false).
         *
         * @return this builder
         */
        public B setIncludeNullDelimiter(final boolean includeNullDelimiter) {
            this.includeNullDelimiter = includeNullDelimiter;
            return asBuilder();
        }

        /**
         * Additional fields to set on each log event.
         *
         * @return this builder
         */
        public B setAdditionalFields(KeyValuePair[] additionalFields) {
            this.additionalFields = additionalFields;
            return asBuilder();
        }
    }

    protected final String eol;
    protected final ObjectWriter objectWriter;
    protected final boolean compact;
    protected final boolean complete;
    protected final boolean includeNullDelimiter;
    protected final Map<String, Object> additionalFields;

    private final static ThreadLocal<LogEvent> currentEvent = new ThreadLocal<>();

    @Deprecated
    protected AbstractJacksonLayout(final Configuration config, final ObjectWriter objectWriter, final Charset charset,
            final boolean compact, final boolean complete, final boolean eventEol, final Serializer headerSerializer,
            final Serializer footerSerializer) {
        this(config, objectWriter, charset, compact, complete, eventEol, headerSerializer, footerSerializer, false);
    }

    @Deprecated
    protected AbstractJacksonLayout(final Configuration config, final ObjectWriter objectWriter, final Charset charset,
            final boolean compact, final boolean complete, final boolean eventEol, final Serializer headerSerializer,
            final Serializer footerSerializer, final boolean includeNullDelimiter) {
        this(config, objectWriter, charset, compact, complete, eventEol, headerSerializer, footerSerializer, includeNullDelimiter, null);
    }

    protected AbstractJacksonLayout(final Configuration config, final ObjectWriter objectWriter, final Charset charset,
            final boolean compact, final boolean complete, final boolean eventEol, final Serializer headerSerializer,
            final Serializer footerSerializer, final boolean includeNullDelimiter,
            final KeyValuePair[] additionalFields) {
        super(config, charset, headerSerializer, footerSerializer);
        this.objectWriter = objectWriter;
        this.compact = compact;
        this.complete = complete;
        this.eol = compact && !eventEol ? COMPACT_EOL : DEFAULT_EOL;
        this.includeNullDelimiter = includeNullDelimiter;
        this.additionalFields = prepareAdditionalFields(config, additionalFields);
    }

    protected static boolean valueNeedsLookup(final String value) {
        return value != null && value.contains("${");
    }

    private static Map<String, Object> prepareAdditionalFields(final Configuration config, final KeyValuePair[] additionalFields) {
        if (additionalFields == null || additionalFields.length == 0) {
            // No fields set
            return Collections.emptyMap();
        }

        // Note: ResolvableValue uses ThreadLocal pointing to currently processed event, thus its toString is thread-dependent
        // Therefor we can reuse same object across multiple threads
        final Map<String, Object> additionalFieldsMap = new LinkedHashMap<>(additionalFields.length);

        for (KeyValuePair pair : additionalFields) {
            final String value = pair.getValue();
            additionalFieldsMap.put(pair.getKey(), valueNeedsLookup(value) ? new ResolvableValue(config, value) : value);
        }

        return additionalFieldsMap;
    }

    /**
     * Formats a {@link org.apache.logging.log4j.core.LogEvent}.
     *
     * @param event The LogEvent.
     * @return The XML representation of the LogEvent.
     */
    @Override
    public String toSerializable(final LogEvent event) {
        final StringBuilderWriter writer = new StringBuilderWriter();
        try {
            toSerializable(event, writer);
            return writer.toString();
        } catch (final IOException e) {
            // Should this be an ISE or IAE?
            LOGGER.error(e);
            return Strings.EMPTY;
        }
    }

    private static LogEvent convertMutableToLog4jEvent(final LogEvent event) {
        // TODO Jackson-based layouts have certain filters set up for Log4jLogEvent.
        // TODO Need to set up the same filters for MutableLogEvent but don't know how...
        // This is a workaround.
        return event instanceof MutableLogEvent
                ? ((MutableLogEvent) event).createMemento()
                : event;
    }

    public void toSerializable(LogEvent event, final Writer writer)
            throws IOException {
        event = convertMutableToLog4jEvent(event);

        if (additionalFields.isEmpty()) {
            objectWriter.writeValue(writer, event);
        } else {
            try {
                currentEvent.set(event);
                objectWriter.writeValue(writer, new LogEventWithAdditionalFields(event, additionalFields));
            } finally {
                currentEvent.remove();
            }
        }

        writer.write(eol);
        if (includeNullDelimiter) {
            writer.write('\0');
        }
        markEvent();
    }

    @JsonRootName(XmlConstants.ELT_EVENT)
    @JacksonXmlRootElement(namespace = XmlConstants.XML_NAMESPACE, localName = XmlConstants.ELT_EVENT)
    public static class LogEventWithAdditionalFields {

        private final Object logEvent;
        private final Map<String, Object> additionalFields;

        public LogEventWithAdditionalFields(Object logEvent, Map<String, Object> additionalFields) {
            this.logEvent = logEvent;
            this.additionalFields = additionalFields;
        }

        @JsonUnwrapped
        public Object getLogEvent() {
            return logEvent;
        }

        @JsonAnyGetter
        @SuppressWarnings("unused")
        public Map<String, Object> getAdditionalFields() {
            return additionalFields;
        }
    }

    protected static class ResolvableValue {

        final StrSubstitutor strSubstitutor;
        final String expression;

        ResolvableValue(Configuration configuration, String expression) {
            if (configuration == null) {
                throw new IllegalArgumentException("configuration needs to be set when there are additional fields with variables");
            }

            this.strSubstitutor = configuration.getStrSubstitutor();
            this.expression = expression;
        }

        @Override
        @JsonValue
        public String toString() {
            final LogEvent logEvent = currentEvent.get();
            return strSubstitutor.replace(logEvent, expression);
        }
    }
}
