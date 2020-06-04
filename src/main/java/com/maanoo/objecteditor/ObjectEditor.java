// Copyright (c) 2020 Akritas Akritidis, see LICENSE.
package com.maanoo.objecteditor;

import java.util.HashMap;


/**
 * ObjectEditor interface.
 *
 * @author Akritas Akritidis
 */
public final class ObjectEditor {

    private ObjectEditor() {}

    public static ObjectEditorWindow show(Object object) {
        return new ObjectEditorWindow(object);
    }

    /** Testing main method */
    public static void main(String[] args) {

        final HashMap<String, String> map = new HashMap<String, String>();
        map.put("key1", "value1");
        map.put("key2", "value2");

        show(new ObjectEditorWindow(map));
    }

}
