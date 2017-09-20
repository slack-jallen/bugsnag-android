package com.bugsnag.android;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringDef;
import android.text.TextUtils;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

final class HandledState implements JsonStream.Streamable {

    @StringDef({REASON_UNHANDLED_EXCEPTION, REASON_STRICT_MODE, REASON_HANDLED_EXCEPTION,
        REASON_USER_SPECIFIED, REASON_CALLBACK_SPECIFIED})
    @Retention(RetentionPolicy.SOURCE)
    @interface SeverityReason {
    }

    static final String REASON_UNHANDLED_EXCEPTION = "unhandledException";
    static final String REASON_STRICT_MODE = "strictMode";
    static final String REASON_HANDLED_EXCEPTION = "handledException";
    static final String REASON_USER_SPECIFIED = "userSpecifiedSeverity";
    static final String REASON_CALLBACK_SPECIFIED = "userCallbackSetSeverity";

    @SeverityReason
    private final String severityReasonType;

    @Nullable
    private final String attributeValue;

    private final Severity defaultSeverity;
    private final boolean unhandled;

    static HandledState valueOf(@SeverityReason String severityReasonType) {
        return valueOf(severityReasonType, null, null);
    }

    static HandledState valueOf(@SeverityReason String severityReasonType,
                                @Nullable Severity severity,
                                @Nullable String attributeValue) {

        if (severityReasonType.equals(REASON_STRICT_MODE) && TextUtils.isEmpty(attributeValue)) {
            throw new IllegalArgumentException("No reason supplied for strictmode");
        }
        if (!severityReasonType.equals(REASON_STRICT_MODE) && !TextUtils.isEmpty(attributeValue)) {
            throw new IllegalArgumentException("attributeValue should not be supplied");
        }
        if (severity != null && !severityReasonType.equals(REASON_USER_SPECIFIED)) {
            throw new IllegalArgumentException("Should not supply severity for non-user specified err");
        }

        switch (severityReasonType) {
            case REASON_UNHANDLED_EXCEPTION:
                return new HandledState(severityReasonType, Severity.ERROR, true, null);
            case REASON_STRICT_MODE:
                return new HandledState(severityReasonType, Severity.WARNING, true, attributeValue);
            case REASON_HANDLED_EXCEPTION:
                return new HandledState(severityReasonType, Severity.WARNING, false, null);
            case REASON_USER_SPECIFIED:
                return new HandledState(severityReasonType, severity, false, null);
            default:
                throw new IllegalArgumentException("Invalid arg for reason: " + severityReasonType);
        }
    }

    private HandledState(String severityReasonType, Severity severity, boolean unhandled,
                         @Nullable String attributeValue) {
        this.severityReasonType = severityReasonType;
        this.defaultSeverity = severity;
        this.unhandled = unhandled;
        this.attributeValue = attributeValue;
    }

    String calculateSeverityReasonType(Severity severity) {
        return defaultSeverity == severity ? severityReasonType : REASON_CALLBACK_SPECIFIED;
    }

    Severity getSeverity() {
        return defaultSeverity;
    }

    boolean isUnhandled() {
        return unhandled;
    }

    @Nullable
    String getAttributeValue() {
        return attributeValue;
    }

    @Override
    public void toStream(@NonNull JsonStream writer) throws IOException {
        writer.beginObject();
        writer.name("severityReason").beginObject()
            .name("type").value(severityReasonType);

        if (attributeValue != null) {
            writer.name("attributes").beginObject()
                .name("violationType").value(attributeValue)
                .endObject();
        }
        writer.endObject().endObject();
    }

}
