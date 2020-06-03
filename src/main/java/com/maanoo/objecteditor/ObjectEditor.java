package com.maanoo.objecteditor;

import java.util.HashMap;


public class ObjectEditor {

    public static void main(String[] args) {

        final HashMap<String, String> map = new HashMap<String, String>() {
            public void setEmpty(boolean empty) {
                if (empty) clear();
            }

            @Override
            public String toString() {
                return super.toString();
            }
        };
        map.put("k\n1", "v1");
        map.put("k2", "v2");

//        System.out.println(ClassInfo.of(map.getClass()));

        final ObjectEditorWindow w = new ObjectEditorWindow(map);
        final ObjectEditorWindow w2 = new ObjectEditorWindow(w);
    }

}
