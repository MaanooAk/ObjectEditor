package com.maanoo.objecteditor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog.ModalExclusionType;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import com.maanoo.objecteditor.ClassInfo.MethodInfo;
import com.maanoo.objecteditor.ClassInfo.MethodInfo.ParameterProvider;


@SuppressWarnings("serial")
public class ObjectEditorWindow extends JFrame {

    public static ObjectEditorWindow show(Object object) {
        return new ObjectEditorWindow(object);
    }

    // ===

    private final Object windowObject;

    private final Class<?> windowTarget;

    private final Tree<Node> tree;
    private final GenericNode root;
    private final JTextField filter;
    private final JTextArea status;

    private HashMap<MethodNode, GenericNode> methodReturns;

    private enum Option {

        ShowFieldsPublic,
        ShowFieldsPrivate,
        ShowFieldsTransient,

        ShowMethodsVoid,
        ShowMethodsNonVoid,
        ShowMethodsWithParams,

        ShowNullElements,
        ShowObjectInternals,
        ShowStringInternals,
        ShowDuplicates,
    }

    // TODO: pass the current options and parsers to children windows

    private final EnumSet<Option> options;
    private final HashMap<Class<?>, Function<String, Object>> parsers;

    private static final String FilterClassPrefix = "$";

    public ObjectEditorWindow(Object object) throws HeadlessException {
        this(object.getClass(), object, null);
    }

    public ObjectEditorWindow(Class<?> cla, Object object, Class<?> target) throws HeadlessException {
        super(target == null ? toDefaultString(object) : target.getName());
        this.windowObject = object;
        this.windowTarget = target;

        options = EnumSet.of(
                Option.ShowFieldsPublic,
                Option.ShowFieldsPrivate,
                Option.ShowMethodsVoid,
                Option.ShowMethodsNonVoid);

        // TODO: extend the support for custom parsers

        parsers = new HashMap<Class<?>, Function<String, Object>>();
        parsers.put(CharSequence.class, new Function<String, Object>() {
            @Override
            public Object apply(String t) {
                return t;
            }
        });
        parsers.put(String.class, parsers.get(CharSequence.class));

        methodReturns = new HashMap<MethodNode, GenericNode>();

        root = new GenericNode(null, 0, cla, object);
        tree = new Tree<Node>(root, new TreeRenderer());

        tree.addMouseListener(new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isRightMouseButton(e)) return;
                final JPopupMenu popup = new JPopupMenu();

                final int selRow = tree.getRowForLocation(e.getX(), e.getY());
                if (selRow > -1) {
                    // select the right clicked node
                    final TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
                    tree.setSelectionPath(selPath);

                    final Node n = (Node) selPath.getLastPathComponent();
                    generatePopupMenuForNode(n, popup);
                } else {
                    generatePopupMenuForTree(popup);
                }

                popup.show(tree, e.getX(), e.getY());
            }
        });

        final JScrollPane treeView = new JScrollPane(tree);

        setLayout(new BorderLayout());
        add(treeView, BorderLayout.CENTER);

        filter = new JTextField();
        filter.setMargin(new Insets(2, 2, 2, 2));
        add(filter, BorderLayout.NORTH);
        filter.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                refreshNodes();
            }
        });

        status = new JTextArea();
        status.setMargin(new Insets(2, 2, 2, 2));
        add(status, BorderLayout.SOUTH);

        tree.addTreeSelectionListener(new TreeSelectionListener() {
            @Override
            public void valueChanged(TreeSelectionEvent e) {
                final Node n = tree.getSelectedNode();
                status.setText(n == null ? "" : n.getStatusText());
            }
        });

        refreshNodes();
        tree.expandRow(0);
        tree.selectFirst();

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        setMinimumSize(new Dimension(500, 600));
        setVisible(true);

        setLocationRelativeTo(null);
    }

    // == Popup menus

    private void generatePopupMenuForTree(final JPopupMenu popup) {
        popup.add(menuItemCheckBoxesHeader("Show fields",
                Option.ShowFieldsPublic, Option.ShowFieldsPrivate, Option.ShowFieldsTransient));
        popup.add(menuItemCheckBox("... public", Option.ShowFieldsPublic));
        popup.add(menuItemCheckBox("... non public", Option.ShowFieldsPrivate));
        popup.add(menuItemCheckBox("... transient", Option.ShowFieldsTransient));
        popup.addSeparator();
        popup.add(menuItemCheckBoxesHeader("Show methods",
                Option.ShowMethodsNonVoid, Option.ShowMethodsVoid, Option.ShowMethodsWithParams));
        popup.add(menuItemCheckBox("... non void", Option.ShowMethodsNonVoid));
        popup.add(menuItemCheckBox("... void", Option.ShowMethodsVoid));
        popup.add(menuItemCheckBox("... with params", Option.ShowMethodsWithParams));
        popup.addSeparator();
        popup.add(menuItemCheckBox("Show null elements", Option.ShowNullElements));
        popup.add(menuItemCheckBox("Show object internals", Option.ShowObjectInternals));
        popup.add(menuItemCheckBox("Show string elements", Option.ShowStringInternals));
        popup.add(menuItemCheckBox("Show duplicates", Option.ShowDuplicates));
        popup.addSeparator();
        {
            final JMenuItem item = new JMenuItem("Expand all");
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    for (int i = 0; i < tree.getRowCount(); i++) {
                        tree.expandRow(i);
                    }
                }
            });
            popup.add(item);
        }
        {
            final JMenuItem item = new JMenuItem("Expand one");
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    for (int i = tree.getRowCount() - 1; i >= 0; i--) {
                        tree.expandRow(i);
                    }
                }
            });
            popup.add(item);
        }
        {
            final JMenuItem item = new JMenuItem("Refresh");
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    refreshNodes();
                }
            });
            popup.add(item);
        }
    }

    private void generatePopupMenuForNode(final Node n, final JPopupMenu popup) {

        if (n instanceof MethodNode) {
            final MethodNode node = (MethodNode) n;

            final JMenuItem item = new JMenuItem(
                    "Call method" + (node.method.getParameterCount() > 0 ? " ..." : ""));

            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    final MethodInfo method = node.method;

                    final Class<?> returnType = method.returnType;
                    final Object ret = method.invoke(node.holder, new ParameterProvider() {
                        @Override
                        public Object get(Class<?> c, String name) {
                            return inputValue(c, name);
                        }
                    });

                    if (returnType != void.class) {
                        final Class<?> cc = (ret instanceof Throwable) ? ret.getClass()
                                : pickClass(returnType, ret);

                        final GenericNode retNode = new GenericNode(null, 0, cc, ret);
//                                    generateNodes(retNode, new HashMap<Object, Node>());

                        methodReturns.put(node, retNode);
                    }

                    refreshNodes();

                }
            });
            popup.add(item);

        } else if (n instanceof CommandNode) {
            final CommandNode node = (CommandNode) n;

            final boolean[] expanded = reloadNodes();

            final Node parent = (Node) node.getParent();
            parent.removeAllChildren();
            final Node copy = ((Node) node.command).copy();
            generateNodes((GenericNode) copy);
            parent.add(copy);

            reloadNodes(expanded);

        } else {
            final GenericNode node = (GenericNode) n;

            if (windowTarget != null) {
                final JMenuItem item = new JMenuItem("Accept");
                item.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        setReturnObject(node.object);
                    }
                });
                popup.add(item);
                popup.addSeparator();
            }

            if (!node.clas.isPrimitive() && node.object != null) {
                final JMenuItem item = new JMenuItem("Popup");
                item.setEnabled(node.object != null && !node.clas.isPrimitive());

                if (item.isEnabled()) item.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        new ObjectEditorWindow(node.object);
                    }
                });
                popup.add(item);
            }
            {
                final JMenuItem item = new JMenuItem("Edit field");
                item.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {

                        try {
                            final Class<?> input = node.clas;
                            final Object param = inputValue(input,
                                    node.field == null ? ("[" + node.index + "]")
                                            : node.field.getName());

                            if (node.field != null) {
                                node.field.set(node.holder, param);

                            } else {
                                Array.set(node.holder, node.index, param);
                            }

                            refreshNodes();

                        } catch (final ReflectiveOperationException ex) {
                            ex.printStackTrace();
                        }
                    }
                });

                popup.add(item);
            }
        }

        if (n.getChildCount() > 0) {
            popup.addSeparator();
            {
                final JMenuItem item = new JMenuItem("Expand");
                item.addActionListener(new ActionListener() {

                    @Override
                    public void actionPerformed(ActionEvent e) {
                        final int offset = tree.getSelectionRows()[0];
                        final int count = n.getAllChildCount();
                        for (int i = 0; i < count; i++) {
                            tree.expandRow(i + offset);
                        }
                    }
                });
                popup.add(item);
            }
        }
    }

    private JMenuItem menuItemCheckBoxesHeader(String text, final Option... option) {

        final JMenuItem item = new JMenuItem(text);
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                final List<Option> list = Arrays.asList(option);
                if (Collections.disjoint(options, list)) {
                    options.addAll(list);
                } else {
                    options.removeAll(list);
                }
                refreshNodes();
            }
        });
        return item;
    }

    private JCheckBoxMenuItem menuItemCheckBox(String text, final Option option) {

        final JCheckBoxMenuItem item = new JCheckBoxMenuItem(text);
        item.setState(options.contains(option));
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (item.getState()) {
                    options.add(option);
                } else {
                    options.remove(option);
                }
                refreshNodes();
            }
        });
        return item;
    }

    // == Nodes

    private void refreshNodes() {
        final boolean[] expanded = reloadNodes();

        root.removeAllChildren();
        generateNodes(root, new IdentityHashMap<Object, Node>(),
                new IdentityHashMap<Object, Node>(), 0);

        final String filterText = filter.getText();
        final Pattern pattern;
        Class<?> target;

        if (filterText.startsWith(FilterClassPrefix)) {
            pattern = null;
            final String type = filterText.substring(FilterClassPrefix.length());
            try {
                target = Class.forName(type);
            } catch (final ClassNotFoundException e) {
                target = ClassInfo.PrimitivesNameMap.get(type);
            }
        } else {
            pattern = filterText.isEmpty() ? null : Pattern.compile(filterText, Pattern.CASE_INSENSITIVE);
            target = windowTarget;
        }

        filterNode(root, pattern, target);

        reloadNodes(expanded);
    }

    private boolean[] reloadNodes() {

        final boolean[] expanded = new boolean[tree.getRowCount()];
        for (int i = 0; i < expanded.length; i++) {
            expanded[i] = tree.isExpanded(i);
        }

        return expanded;
    }

    private void reloadNodes(boolean[] expanded) {

        final DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        model.reload(root);

        for (int i = 0; i < expanded.length && i < tree.getRowCount(); i++) {
            if (expanded[i]) tree.expandRow(i);
        }

        tree.expand(new Predicate<ObjectEditorWindow.Node>() {
            @Override
            public boolean test(Node t) {
                return (t instanceof MethodNode);
            }
        }, false);
    }

    private void filterNode(Node node, final Pattern pattern, Class<?> target) {

        for (int i = 0; i < node.getChildCount(); i++) {
            final Node n = (Node) node.getChildAt(i);

            if (!(n instanceof MethodNode)) {
                filterNode(n, pattern, target);
            }

            final boolean show;
            if (n.getChildCount() > 0) {
                show = true;

            } else if (n instanceof MethodNode) {
                final MethodInfo method = ((MethodNode) n).method;

                show = (pattern == null || pattern.matcher(method.getName()).find()) &&
                        (target == null || target.isAssignableFrom(method.returnType));

            } else if (n instanceof GenericNode) {
                final GenericNode child = (GenericNode) n;

                show = (pattern == null || pattern.matcher(child.toString()).find()) &&
                        (target == null || target.isAssignableFrom(child.clas));

            } else if (n instanceof CommandNode) {
                show = (pattern == null && target == null);

            } else {
                show = true;
            }

            if (!show) {
                node.remove(i);
                i--;
            }
        }
    }

    private static abstract class Node extends Tree.Node {

        public Node copy() {
            throw new RuntimeException();
        }

        private String string;

        @Override
        public String toString() {
            if (string != null) return string;
            return string = getString();
        }

        protected abstract String getString();

        protected abstract String getStatusText();
    }

    private static abstract class HolderNode extends Node {

        public final Object holder;

        public HolderNode(Object holder) {
            super();
            this.holder = holder;
        }

    }

    private static class MethodNode extends HolderNode {

        public final MethodInfo method;

        public MethodNode(Object holder, MethodInfo method) {
            super(holder);
            this.method = method;
        }

        @Override
        public MethodNode copy() {
            return new MethodNode(holder, method);
        }

        @Override
        protected String getString() {
            return method.toString();
        }

        @Override
        protected String getStatusText() {
            return method.toString() + " :: " + method.getDeclaringClass();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((holder == null) ? 0 : holder.hashCode());
            result = prime * result + ((method.method == null) ? 0 : method.method.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            final MethodNode other = (MethodNode) obj;
            if (holder == null) {
                if (other.holder != null) return false;
            } else if (!holder.equals(other.holder)) return false;
            if (method.method == null) {
                if (other.method.method != null) return false;
            } else if (!method.method.equals(other.method.method)) return false;
            return true;
        }

    }

    private static class GenericNode extends Node {

        // TODO: split into sub classes

        public final Object holder;
        public final Field field;
        public final int index;

        public final Class<?> clas;
        public final Object object;

        public GenericNode(Object holder, Field field, Class<?> clas, Object object) {
            this(holder, field, 0, clas, object);
        }

        public GenericNode(Object holder, int index, Class<?> clas, Object object) {
            this(holder, null, index, clas, object);
        }

        private GenericNode(Object holder, Field field, int index, Class<?> clas, Object object) {
            this.holder = holder;
            this.field = field;
            this.index = index;
            this.clas = clas;
            this.object = object;
        }

        @Override
        public GenericNode copy() {
            final GenericNode node = new GenericNode(holder, field, index, clas, object);
//            if (children != null) for (final Object i : children) {
//                node.add(((Node) i).copy());
//            }
            return node;
        }

        @Override
        protected String getString() {

            if (clas.isPrimitive() || clas == String.class || object == null) {
                return (field == null ? ("[" + index + "]") : field.getName()) + " : " + clas.getSimpleName()
                        + " = " + object;
            } else {
                return (field == null ? ("[" + index + "]") : field.getName()) + " : " + clas.getSimpleName();
            }
        }

        @Override
        protected String getStatusText() {
            final int count = getAllChildCount();
            if (count == 0) {
                return ((field != null ? field : (index + "")) + "");
            } else {
                return ("(" + count + ") " + (field != null ? field : (index + "")));
            }
        }

    }

    private static class CommandNode extends Node {

        public final String text;
        public final Object command;

        public CommandNode(String text, Object command) {
            this.text = text;
            this.command = command;
        }

        @Override
        protected String getString() {
            return text;
        }

        @Override
        protected String getStatusText() {
            return "right click to expand";
        }

    }

    private static class TreeRenderer extends Tree.Renderer<Node> {

        private final Icon iconPrimitive;
        private final Icon iconObjectNull;
        private final Icon iconObject;
        private final Icon iconThrowable;
        private final Icon iconMethod;
        private final Icon iconMethodSetter;
        private final Icon iconCommand;

        public TreeRenderer() {
            super();

            iconPrimitive = new Tree.RectangleIcon(base, new Color(0x008000), true, 6);
            iconObjectNull = new Tree.RectangleIcon(base, new Color(0x008000), false, 2);
            iconObject = new Tree.RectangleIcon(base, new Color(0x008000), true, 2);
            iconThrowable = new Tree.RectangleIcon(base, new Color(0xF34000), true, 2);
            iconMethod = new Tree.CircleIcon(base, new Color(0x0087EA), true, 4);
            iconMethodSetter = new Tree.CircleIcon(base, new Color(0xEA8100), true, 4);
            iconCommand = new Tree.CircleIcon(base, new Color(0x37AFBC), true, 4);
        }

        @Override
        public void handle(Node node, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            if (node instanceof MethodNode) {
                setIcon(((MethodNode) node).method.getParameterCount() == 0 ? iconMethod : iconMethodSetter);

            } else if (node instanceof GenericNode) {

                if (((GenericNode) node).clas.isPrimitive()) {
                    setIcon(iconPrimitive);
                } else if (((GenericNode) node).object == null) {
                    setIcon(iconObjectNull);
                } else if (((GenericNode) node).object instanceof Throwable) {
                    setIcon(iconThrowable);
                } else {
                    setIcon(iconObject);
                }

            } else if (node instanceof CommandNode) {
                setIcon(iconCommand);

            }
        }

    }

    private Object inputValue(Class<?> input, String name) {

        if (input.isPrimitive()) {
            final String text = JOptionPane.showInputDialog(name + " : " + input);

            if (input == boolean.class) {
                return Boolean.parseBoolean(text);

            } else if (input == int.class) {
                return Integer.parseInt(text);
            } else if (input == float.class) {
                return Float.parseFloat(text);
            } else if (input == double.class) {
                return Double.parseDouble(text);
            } else if (input == byte.class) {
                return Byte.parseByte(text);
            } else if (input == short.class) {
                return Short.parseShort(text);
            } else if (input == long.class) {
                return Long.parseLong(text);

            } else if (input == char.class) {
                // TODO: parse escapes
                return text.charAt(0);

            } else {
                throw new RuntimeException(input.toString());
            }

        } else if (parsers.containsKey(input)) {
            final String text = JOptionPane.showInputDialog(name + " : " + input);

            return parsers.get(input).apply(text);

        } else {

            // TODO this needs a clean up

            final ObjectEditorWindow sub = new ObjectEditorWindow(windowObject.getClass(), windowObject, input);

            sub.setModalExclusionType(ModalExclusionType.APPLICATION_EXCLUDE);

            final JDialog dialog = sub.dialog = new JDialog(this, true);
            dialog.setLocationRelativeTo(null);
            dialog.setMinimumSize(new Dimension(100, 0));
            dialog.setVisible(true);

            if (sub.returnObject == NoReturnObject) throw new RuntimeException("No object selected");
            return sub.returnObject;
        }
    }

    // TODO change how the wait is performed, this is a mess
    private JDialog dialog;
    private static final Object NoReturnObject = new Object();
    public Object returnObject = NoReturnObject;

    public void setReturnObject(Object returnObject) {
        this.returnObject = returnObject;

        if (dialog != null) dialog.setVisible(false);
        dispose();
    }

    private void generateNodes(GenericNode root) {

        generateNodes(root, new IdentityHashMap<Object, ObjectEditorWindow.Node>(),
                new IdentityHashMap<Object, ObjectEditorWindow.Node>(), 0);
    }

    private void generateNodes(GenericNode root, IdentityHashMap<Object, Node> parents,
            IdentityHashMap<Object, Node> nodes, int depth) {

        final Object rootObject = root.object;
        if (rootObject == null) return;

        generateNodes(root, parents, nodes, root.clas, depth);
    }

    private Class<?> pickClass(Class<?> superclass, Object object) {
        if (superclass.isPrimitive() || object == null) return superclass;
        return object.getClass();
    }

    private void generateNodes(GenericNode root, IdentityHashMap<Object, Node> parents,
            IdentityHashMap<Object, Node> renodes, Class<?> c, int depth) {

        if (c == null || c.isPrimitive()) return;

        if (!options.contains(Option.ShowStringInternals)) if (c == String.class) return;

        final Object rootObject = root.object;
        if (rootObject == null) return;

        if (c.isArray()) {
            final Class<?> comc = c.getComponentType();

            final boolean optionShowNullElements = options.contains(Option.ShowNullElements);

            for (int i = 0, n = Array.getLength(rootObject); i < n; i++) {
                final Object element = Array.get(rootObject, i);

                if (!optionShowNullElements) if (element == null) {
                    continue;
                }

                final Class<?> cc = pickClass(comc, element);

                final GenericNode node = new GenericNode(rootObject, i, cc, element);
                root.add(node);

                generateNodesPropagate(cc, element, node, parents, renodes, depth);
            }
            return;
        }

        final ClassInfo info = ClassInfo.of(c);

        final boolean optionShowFieldsPublic = options.contains(Option.ShowFieldsPublic);
        final boolean optionShowFieldsPrivate = options.contains(Option.ShowFieldsPrivate);
        final boolean optionShowFieldsTransient = options.contains(Option.ShowFieldsTransient);
        final boolean optionShowFields = optionShowFieldsPrivate || optionShowFieldsPublic || optionShowFieldsTransient;

        for (final Field field : info.getFields()) {

            Object object;
            try {
                object = field.get(rootObject);
            } catch (final IllegalArgumentException e) {
                throw new RuntimeException(e);
            } catch (final IllegalAccessException e) {
                throw new RuntimeException(e);
            }

            final Class<?> cc = pickClass(field.getType(), object);

            if (!optionShowFields && (cc.isPrimitive() || object == null)) continue;
            if (!optionShowFieldsPublic && Modifier.isPublic(field.getModifiers())) continue;
            if (!optionShowFieldsPrivate && !Modifier.isPublic(field.getModifiers())) continue;
            if (!optionShowFieldsTransient && Modifier.isTransient(field.getModifiers())) continue;

            final GenericNode node = new GenericNode(rootObject, field, cc, object);
            root.add(node);

            generateNodesPropagate(cc, object, node, parents, renodes, depth);
        }

        final boolean optionShowMethodsNonVoid = options.contains(Option.ShowMethodsNonVoid);
        final boolean optionShowMethodsVoid = options.contains(Option.ShowMethodsVoid);
        final boolean optionShowMethods = optionShowMethodsNonVoid || optionShowMethodsVoid;
        final boolean optionShowMethodsWithParams = options.contains(Option.ShowMethodsWithParams);
        final boolean optionShowObjectInternals = options.contains(Option.ShowObjectInternals);

        if (optionShowMethods) for (final MethodInfo method : info.getMethods()) {

            if (!optionShowMethodsNonVoid && method.returnType != void.class) continue;
            if (!optionShowMethodsVoid && method.returnType == void.class) continue;
            if (!optionShowMethodsWithParams && method.getParameterCount() > 0) continue;
            if (!optionShowObjectInternals && method.getDeclaringClass() == Object.class) continue;

            final MethodNode node = new MethodNode(rootObject, method);
            root.add(node);

            if (methodReturns.containsKey(node)) {
                final GenericNode returnNode = methodReturns.get(node).copy();

                node.add(returnNode);

                generateNodesPropagate(returnNode.clas, returnNode.object, returnNode, parents, renodes, depth);
            }
        }
    }

    private void generateNodesPropagate(final Class<?> c, final Object element,
            final Node node, IdentityHashMap<Object, Node> parents, IdentityHashMap<Object, Node> renodes, int depth) {

        if (c.isPrimitive() || element == null) {

        } else if (parents.containsKey(element)) {

            final CommandNode child = new CommandNode("parent", parents.get(element));
            node.add(child);

        } else if (renodes.containsKey(element)) {

            final CommandNode child = new CommandNode("reference", renodes.get(element));
            node.add(child);

        } else {
            parents.put(element, node);
            if (!options.contains(Option.ShowDuplicates)) renodes.put(element, node);
            generateNodes((GenericNode) node, parents, renodes, depth + 1);
            parents.remove(element);
        }
    }

    private static String toDefaultString(Object object) {
        if (object == null) return "@" + System.identityHashCode(object);
        return object.getClass().getName() + "@" + System.identityHashCode(object);
    }

    // ===

    private static class Tree<T extends Tree.Node> extends JTree {

        public abstract static class Node extends DefaultMutableTreeNode {

            @Override
            public abstract String toString();

            public int getAllChildCount() {
                int sum = getChildCount();
                if (sum == 0) return 0;
                for (final Object i : children) {
                    sum += ((Node) i).getAllChildCount();
                }
                return sum;
            }

        }

        private static abstract class BaseIcon implements Icon {

            private final Icon base;

            protected final Color color;
            protected final boolean fill;
            protected final int space;

            public BaseIcon(Icon base, Color color, boolean fill, int space) {
                this.base = base;
                this.color = color;
                this.fill = fill;
                this.space = space;
            }

            @Override
            public int getIconWidth() {
                return base.getIconWidth();
            }

            @Override
            public int getIconHeight() {
                return base.getIconHeight();
            }
        }

        public static class RectangleIcon extends BaseIcon {

            public RectangleIcon(Icon base, Color color, boolean fill, int space) {
                super(base, color, fill, space);
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                final int side = getIconWidth() - 2 * space;

                g.setColor(color);
                if (fill) {
                    g.fillRect(x + space, y + getIconHeight() / 2 - side / 2, side, side);
                } else {
                    g.drawRect(x + space, y + getIconHeight() / 2 - side / 2, side, side);
                }
            }

        }

        public static class CircleIcon extends BaseIcon {

            public CircleIcon(Icon base, Color color, boolean fill, int space) {
                super(base, color, fill, space);
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                final int side = getIconWidth() - 2 * space;

                g.setColor(color);
                if (fill) {
                    g.fillOval(x + space, y + getIconHeight() / 2 - side / 2, side, side);
                } else {
                    g.drawOval(x + space, y + getIconHeight() / 2 - side / 2, side, side);
                }
            }
        }

        public static abstract class Renderer<T extends Node> extends DefaultTreeCellRenderer {

            protected final Icon base;

            public Renderer() {
                super();
                base = getDefaultClosedIcon();
            }

            @Override
            @SuppressWarnings("unchecked")
            public final Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded,
                    boolean leaf, int row, boolean hasFocus) {
                final Component component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row,
                        hasFocus);

                final T node = (T) value;
                handle(node, sel, expanded, leaf, row, hasFocus);

                return component;
            }

            public abstract void handle(T node, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus);

        }

        //

        public Tree(T top, Renderer<T> renderer) {
            super(top);

            if (renderer != null) setCellRenderer(renderer);

            getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        }

        @SuppressWarnings("unchecked")
        public T getSelectedNode() {
            return (T) getLastSelectedPathComponent();
        }

        @SuppressWarnings("unchecked")
        public T getNodeAtRow(int index) {
            return (T) getPathForRow(index).getLastPathComponent();
        }

        public void selectFirst() {
            getSelectionModel().setSelectionPath(getPathForRow(0));
        }

        public void expand(Predicate<T> condition, boolean collapse) {
            expandRow(0);

            for (int i = 1; i < getRowCount(); i++) {
                final T node = getNodeAtRow(i);

                if (condition.test(node)) expandRow(i);
                else if (collapse) collapseRow(i);
            }
        }

        @Override
        public TreeSelectionModel getSelectionModel() {

            return super.getSelectionModel();
        }

    }

    // ===

    public interface Function<T, R> {
        R apply(T t);
    }

    public interface Predicate<T> {
        boolean test(T t);
    }
}
