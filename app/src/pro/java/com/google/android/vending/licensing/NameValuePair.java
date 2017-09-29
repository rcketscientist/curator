package com.google.android.vending.licensing;

// Hack for API 23
// http://stackoverflow.com/questions/32115018/lvl-library-and-android-marshmallow
public class NameValuePair
{
    private String name;
    private String value;

    public NameValuePair(String n, String v) {
        name = n;
        value = v;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}
