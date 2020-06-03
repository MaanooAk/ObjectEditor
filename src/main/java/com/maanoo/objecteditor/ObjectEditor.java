package com.maanoo.objecteditor;

import java.util.HashMap;


public class ObjectEditor {

    public static void main(String[] args) {

        final HashMap<String, String> map = new HashMap<String, String>();
        map.put("key1", "value1");
        map.put("key2", "value2");

        new ObjectEditorWindow(new ObjectEditorWindow(map));
    }

}
