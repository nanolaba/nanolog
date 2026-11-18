package com.nanolaba.logging;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.nanolaba.logging.LogEntry.LogEntryLevel.INFO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The structural rendering of values without a {@code toString()} of their own: {@code LOG.dbg} always uses it,
 * {@code {}} arguments with {@code nanolog.dump=auto}.
 */
class DumpTest extends AbstractLoggerTest {

    @BeforeEach
    public void forget() {
        Dump.clearCache();
    }

    @AfterEach
    public void restore() {
        System.clearProperty(DefaultLoggerCreator.PROPERTY_DUMP);
        LOG.init();
        Dump.clearCache();
    }

    static class Base {
        long id = 7;
    }

    static class Order extends Base {
        static int created = 3;
        String name = "book";
        int[] qty = {1, 2};
        Line line = new Line("A-1", 2);
    }

    static class Line {
        private final String sku;
        private final int qty;

        Line(String sku, int qty) {
            this.sku = sku;
            this.qty = qty;
        }
    }

    class Inner {
        int x = 1;
    }

    static class Described {
        Line line = new Line("A-1", 2);

        @Override
        public String toString() {
            return "custom";
        }
    }

    static class DescribedChild extends Described {
        int y = 2;
    }

    static class Failing {
        Object broken = new Object() {
            @Override
            public String toString() {
                throw new IllegalStateException("broken");
            }
        };
    }

    static class Node {
        String name;
        Node next;
        List<Object> children = new ArrayList<>();

        Node(String name, Node next) {
            this.name = name;
            this.next = next;
        }
    }

    static class Pair {
        Object left;
        Object right;

        Pair(Object left, Object right) {
            this.left = left;
            this.right = right;
        }
    }

    static class Text {
        String value;

        Text(String value) {
            this.value = value;
        }
    }

    static class Account {
        String user = "alice";
        String password = "p1";
        String apiKey = "k1";
        String api_key = "k2";
        String accessToken = "t1";
        String clientSecret = "s1";
        char[] credentials = {'x'};
        String authorization = "Bearer x";
        String passwd = "p2";
        String token = null;
    }

    static class Cart extends ArrayList<String> {
        @Override
        public String toString() {
            return "cart";
        }
    }

    static class PlainCart extends ArrayList<String> {
    }

    static class Registry extends HashMap<String, Integer> {
        @Override
        public String toString() {
            return "registry";
        }
    }

    static class Customer$HibernateProxy$Q1 {
        @Override
        public String toString() {
            throw new IllegalStateException("could not initialize proxy - no Session");
        }
    }

    static class Service$$EnhancerBySpringCGLIB$$1 {
        int calls = 1;
    }

    static class Holder {
        Object lock = new ReentrantLock();
        Object date = LocalDate.of(2026, 11, 17);
        Object number = new AtomicInteger(5);
        Object duration = Duration.ofSeconds(90);
        Object thread = Thread.currentThread();
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private static String numbers(int from, int to) {
        return IntStream.range(from, to).mapToObj(String::valueOf).collect(Collectors.joining(", "));
    }

    // dbg style

    @Test
    public void testFieldsOfTheClassAndItsSuperclasses() {
        assertEquals("Order{id=7, name=\"book\", qty=[1, 2], line=Line{sku=\"A-1\", qty=2}}", Dump.dump(new Order()),
                "superclass fields first, static fields skipped, private fields read");
    }

    @Test
    public void testSyntheticFieldsAreSkipped() {
        assertEquals("Inner{x=1}", Dump.dump(new Inner()), "no this$0");
    }

    @Test
    public void testOwnToStringIsUsed() {
        assertEquals("custom", Dump.dump(new Described()));
        assertEquals("custom", Dump.dump(new DescribedChild()), "inherited from a class of the application");
        assertEquals("Pair{left=custom, right=null}", Dump.dump(new Pair(new Described(), null)));
    }

    @Test
    public void testBrokenToStringOfAFieldValue() {
        assertEquals("Failing{broken=[FAILED toString()]}", Dump.dump(new Failing()));
    }

    @Test
    public void testScalars() {
        assertEquals("null", Dump.dump(null));
        assertEquals("\"a\\n\"", Dump.dump("a\n"));
        assertEquals("'c'", Dump.dump('c'));
        assertEquals("'\\''", Dump.dump('\''));
        assertEquals("'\"'", Dump.dump('"'));
        assertEquals("'\\t'", Dump.dump('\t'));
        assertEquals("17", Dump.dump(17L));
        assertEquals("true", Dump.dump(true));
        assertEquals("INFO", Dump.dump(INFO));
    }

    @Test
    public void testJdkValuesAreRenderedThroughTheirPublicApi() {
        String dump = Dump.dump(new Holder());

        assertTrue(dump.startsWith("Holder{lock=java.util.concurrent.locks.ReentrantLock@"), dump);
        assertTrue(dump.contains("[Unlocked], date=2026-11-17, number=5, duration=PT1M30S, thread=Thread["), dump);
        assertTrue(Dump.dump(new Object()).startsWith("java.lang.Object@"));
    }

    @Test
    public void testCollectionsMapsArraysAndOptionals() {
        assertEquals("[1, \"a\", null, 'c']", Dump.dump(Arrays.asList(1, "a", null, 'c')));
        TreeMap<String, Object> map = new TreeMap<>();
        map.put("a", 1);
        map.put("b", Arrays.asList(1, 2));
        assertEquals("{\"a\"=1, \"b\"=[1, 2]}", Dump.dump(map));
        assertEquals("Optional[\"x\"]", Dump.dump(Optional.of("x")));
        assertEquals("Optional.empty", Dump.dump(Optional.empty()));
        assertEquals("['a', 'b']", Dump.dump(new char[]{'a', 'b'}));
        assertEquals("[true, false]", Dump.dump(new boolean[]{true, false}));
        assertEquals("[[1], [2, 3]]", Dump.dump(new int[][]{{1}, {2, 3}}));
        assertEquals("[\"a\", Line{sku=\"b\", qty=1}]", Dump.dump(new Object[]{"a", new Line("b", 1)}));
        assertEquals("[]", Dump.dump(Collections.emptyList()));
        assertEquals("{}", Dump.dump(Collections.emptyMap()));
    }

    @Test
    public void testCollectionsOfTheApplication() {
        Cart cart = new Cart();
        cart.add("a");
        PlainCart plain = new PlainCart();
        plain.add("a");
        Registry registry = new Registry();
        registry.put("a", 1);

        assertEquals("cart", Dump.dump(cart), "its own toString()");
        assertEquals("[\"a\"]", Dump.dump(plain), "toString() of the JDK: rendered item by item");
        assertEquals("registry", Dump.dump(registry));
    }

    @Test
    public void testCycles() {
        Node node = new Node("a", null);
        node.next = node;
        List<Object> list = new ArrayList<>();
        list.add(list);
        Map<String, Object> map = new HashMap<>();
        map.put("self", map);
        Object[] array = new Object[1];
        array[0] = array;

        assertEquals("Node{name=\"a\", next=<cycle: Node>, children=[]}", Dump.dump(node));
        assertEquals("[<cycle: ArrayList>]", Dump.dump(list));
        assertEquals("{\"self\"=<cycle: HashMap>}", Dump.dump(map));
        assertEquals("[<cycle: Object[]>]", Dump.dump(array));
    }

    @Test
    public void testSharedValuesAreNotCycles() {
        Line line = new Line("A-1", 2);
        List<Integer> list = Collections.singletonList(1);
        Map<String, Integer> map = Collections.singletonMap("k", 1);
        int[] array = {1};

        assertEquals("Pair{left=Line{sku=\"A-1\", qty=2}, right=Line{sku=\"A-1\", qty=2}}", Dump.dump(new Pair(line, line)));
        assertEquals("Pair{left=[1], right=[1]}", Dump.dump(new Pair(list, list)));
        assertEquals("Pair{left={\"k\"=1}, right={\"k\"=1}}", Dump.dump(new Pair(map, map)));
        assertEquals("Pair{left=[1], right=[1]}", Dump.dump(new Pair(array, array)));
    }

    @Test
    public void testDepth() {
        Node chain = new Node("a", new Node("b", new Node("c", new Node("d", new Node("e", null)))));

        assertEquals("Node{name=\"a\", next=Node{name=\"b\", next=Node{name=\"c\", next=Node{name=\"d\", " +
                     "next=Node{... (+3 more)}, children=[]}, children=[]}, children=[]}, children=[]}", Dump.dump(chain));
        Object lists = Collections.singletonList(Collections.singletonList(Collections.singletonList(
                Collections.singletonList(Collections.singletonList(1)))));
        assertEquals("[[[[[... (+1 more)]]]]]", Dump.dump(lists));
        Object maps = Collections.singletonList(Collections.singletonList(Collections.singletonList(
                Collections.singletonList(Collections.singletonMap("k", 1)))));
        assertEquals("[[[[{... (+1 more)}]]]]", Dump.dump(maps));
        Object empty = Collections.singletonList(Collections.singletonList(Collections.singletonList(
                Collections.singletonList(Collections.emptyList()))));
        assertEquals("[[[[[]]]]]", Dump.dump(empty), "nothing was cut");
        Object map = Collections.singletonList(Collections.singletonList(Collections.singletonList(
                Collections.singletonMap(Collections.singletonList(1), Collections.singletonList(2)))));
        assertEquals("[[[{[... (+1 more)]=[... (+1 more)]}]]]", Dump.dump(map), "keys and values of a map are one level deeper");
    }

    @Test
    public void testItemLimit() {
        List<Integer> hundred = IntStream.range(0, 100).boxed().collect(Collectors.toList());
        List<Integer> more = IntStream.range(0, 150).boxed().collect(Collectors.toList());
        Map<Integer, Integer> map = new TreeMap<>();
        for (int i = 0; i < 101; i++) {
            map.put(i, i);
        }

        assertEquals("[" + numbers(0, 100) + "]", Dump.dump(hundred));
        assertEquals("[" + numbers(0, 100) + ", ... (+50 more)]", Dump.dump(more));
        assertEquals("[" + numbers(0, 100) + ", ... (+1 more)]", Dump.dump(IntStream.range(0, 101).toArray()));
        assertTrue(Dump.dump(map).endsWith(", 99=99, ... (+1 more)}"), Dump.dump(map));
    }

    @Test
    public void testStringLimit() {
        String thousand = repeat('a', 1000);

        assertEquals("Text{value=\"" + thousand + "\"}", Dump.dump(new Text(thousand)));
        assertEquals("Text{value=\"" + thousand + "\"... (+1 more chars)}", Dump.dump(new Text(thousand + "b")));
    }

    @Test
    public void testRedaction() {
        assertEquals("Account{user=\"alice\", password=***, apiKey=***, api_key=***, accessToken=***, clientSecret=***, " +
                     "credentials=***, authorization=***, passwd=***, token=null}", Dump.dump(new Account()),
                "a secret that is null shows as null");
        Map<Object, Object> headers = new LinkedHashMap<>();
        headers.put("Password", "p");
        headers.put("X-Api-Key", "k");
        headers.put("user", "bob");
        headers.put(1, 2);
        assertEquals("{\"Password\"=***, \"X-Api-Key\"=***, \"user\"=\"bob\", 1=2}", Dump.dump(headers));
    }

    @Test
    public void testSecretNames() {
        assertTrue(Dump.isSecret("PASSWORD"));
        assertTrue(Dump.isSecret("api_key"));
        assertTrue(Dump.isSecret("x-api-key"));
        assertTrue(Dump.isSecret("refreshToken"));
        assertFalse(Dump.isSecret("user"));
        assertFalse(Dump.isSecret("key"));
    }

    @Test
    public void testProxiesAreNamedWithoutCallingThem() {
        Runnable lambda = () -> {
        };
        Runnable proxy = (Runnable) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Runnable.class},
                (self, method, args) -> "handled");

        assertEquals(LogEntry.simpleName(lambda.getClass()), Dump.dump(lambda));
        assertTrue(Dump.dump(lambda).contains("$$Lambda"), Dump.dump(lambda));
        assertTrue(Dump.dump(proxy).startsWith("$Proxy"), Dump.dump(proxy));
        assertEquals("Customer$HibernateProxy$Q1", Dump.dump(new Customer$HibernateProxy$Q1()), "no lazy loading");
        assertEquals("Service$$EnhancerBySpringCGLIB$$1", Dump.dump(new Service$$EnhancerBySpringCGLIB$$1()));
    }

    @Test
    public void testAFieldThatCannotBeReadIsMarked() throws Exception {
        StringBuilder out = new StringBuilder();
        new Dump().field(out, Line.class.getDeclaredField("sku"), new Object(), 0);
        out.append(", ");
        new Dump().field(out, Base.class.getDeclaredField("id"), new Base(), 0);
        out.append(", ");
        new Dump().field(out, String.class.getDeclaredField("hash"), "abc", 0);

        assertEquals("sku=<inaccessible>, id=7, hash=<inaccessible>", out.toString(),
                "a field of another class, a readable one, a private field without access");
    }

    @Test
    public void testAFailureBecomesAMarker() {
        List<Object> broken = new AbstractList<Object>() {
            @Override
            public Object get(int index) {
                throw new IllegalStateException("broken");
            }

            @Override
            public int size() {
                return 1;
            }
        };

        assertEquals("[FAILED dump()]", Dump.dump(broken));
        assertEquals("[FAILED dump()]", Dump.argument(broken));
    }

    @Test
    public void testRecordsAreRenderedByTheirComponents(@TempDir Path dir) throws Exception {
        String version = System.getProperty("java.specification.version");
        assumeTrue(!version.startsWith("1.") && Integer.parseInt(version) >= 16, "records need Java 16");
        Path source = dir.resolve("Point.java");
        Files.write(source, "public record Point(int x, int[] ys, String password) {}".getBytes(StandardCharsets.UTF_8));
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", dir.toString(), source.toString()));

        try (URLClassLoader loader = new URLClassLoader(new URL[]{dir.toUri().toURL()})) {
            Object point = loader.loadClass("Point").getConstructor(int.class, int[].class, String.class)
                    .newInstance(1, new int[]{2, 3}, "p");

            assertEquals("Point[x=1, ys=[2, 3], password=***]", Dump.dump(point));
            assertEquals(point.toString(), Dump.argument(point), "a record has its own toString()");
        }
    }

    // {} arguments with nanolog.dump=auto

    @Test
    public void testArgumentsWithTheirOwnToStringAreUnchanged() {
        List<String> list = Arrays.asList("a", "b");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("password", "p");
        map.put("list", list);
        String[] strings = {"a", "b"};
        int[][] nested = {{1}, {2, 3}};
        List<Integer> large = IntStream.range(0, 150).boxed().collect(Collectors.toList());
        String longText = repeat('a', 1500);

        assertEquals("null", Dump.argument(null));
        assertEquals("a", Dump.argument("a"));
        assertEquals("c", Dump.argument('c'));
        assertEquals("17", Dump.argument(17));
        assertEquals("custom", Dump.argument(new Described()));
        assertEquals(list.toString(), Dump.argument(list));
        assertEquals(map.toString(), Dump.argument(map), "no redaction, no quotes");
        assertEquals(Optional.of("x").toString(), Dump.argument(Optional.of("x")));
        assertEquals(Optional.empty().toString(), Dump.argument(Optional.empty()));
        assertEquals(Arrays.toString(strings), Dump.argument(strings));
        assertEquals(Arrays.deepToString(nested), Dump.argument(nested));
        assertEquals(large.toString(), Dump.argument(large), "no item limit");
        assertEquals("[" + longText + "]", Dump.argument(Collections.singletonList(longText)), "no string limit");
        assertEquals("cart", Dump.argument(new Cart()));
    }

    @Test
    public void testArgumentsWithoutToStringAreDumped() {
        Line line = new Line("A-1", 2);
        Runnable lambda = () -> {
        };
        Runnable proxy = (Runnable) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{Runnable.class},
                (self, method, args) -> "handled");

        assertEquals("Line{sku=\"A-1\", qty=2}", Dump.argument(line));
        assertEquals("[a, Line{sku=\"A-1\", qty=2}]", Dump.argument(Arrays.asList("a", line)), "in a collection");
        assertEquals("{a=Line{sku=\"A-1\", qty=2}}", Dump.argument(Collections.singletonMap("a", line)), "in a map");
        assertEquals("[Line{sku=\"A-1\", qty=2}]", Dump.argument(new Object[]{line}), "in an array");
        assertEquals("Optional[Line{sku=\"A-1\", qty=2}]", Dump.argument(Optional.of(line)));
        assertEquals("Account{user=\"alice\", password=***, apiKey=***, api_key=***, accessToken=***, clientSecret=***, " +
                     "credentials=***, authorization=***, passwd=***, token=null}", Dump.argument(new Account()));
        assertEquals(LogEntry.simpleName(lambda.getClass()), Dump.argument(lambda));
        assertEquals("handled", Dump.argument(proxy), "a proxy with its own toString()");
    }

    @Test
    public void testAutoChangesTheFormattingOfArguments() {
        Line line = new Line("A-1", 2);

        String before = new LogEntry(INFO, null, null, "Created {}", line).getFormattedMessage();
        System.setProperty(DefaultLoggerCreator.PROPERTY_DUMP, " Auto ");
        LOG.init();
        String after = new LogEntry(INFO, null, null, "Created {} for {}", line, "alice").getFormattedMessage();

        assertTrue(before.startsWith("Created com.nanolaba.logging.DumpTest$Line@"), before);
        assertEquals("Created Line{sku=\"A-1\", qty=2} for alice", after);
    }

    @Test
    public void testAutoAppliesToJsonArguments() {
        System.setProperty(DefaultLoggerCreator.PROPERTY_DUMP, "auto");
        LOG.init();

        new JsonLogger().log(new LogEntry(INFO, null, null, "Created {}", new Line("A-1", 2)));

        String json = getOutAndClear();
        assertTrue(json.contains("\"message\":\"Created Line{sku=\\\"A-1\\\", qty=2}\""), json);
        assertTrue(json.contains("\"args\":[\"Line{sku=\\\"A-1\\\", qty=2}\"]"), json);
    }

    @Test
    public void testTheSettingIsOffByDefaultAndChecked() {
        LOG.init();
        assertFalse(Dump.auto);

        System.setProperty(DefaultLoggerCreator.PROPERTY_DUMP, "auto");
        LOG.init();
        assertTrue(Dump.auto);

        System.setProperty(DefaultLoggerCreator.PROPERTY_DUMP, "OFF");
        LOG.init();
        assertFalse(Dump.auto);
        assertEquals("", getErrAndClear());

        System.setProperty(DefaultLoggerCreator.PROPERTY_DUMP, "always");
        LOG.init();
        assertFalse(Dump.auto);
        assertEquals("[NANOLOG] Invalid value \"always\" of \"nanolog.dump\": expected auto or off; set as a system property" + System.lineSeparator(),
                getErrAndClear());
    }
}
