package cn.servicehub.catalog.config;

/** Only these non-executable controls can be introduced by a catalog manager. */
public enum ConfigurableFormFieldType {
    TEXT, LONG_TEXT, SINGLE_SELECT, MULTI_SELECT, DATETIME, BOOLEAN, TAGS, CI_REFERENCE, RICH_TEXT
}
