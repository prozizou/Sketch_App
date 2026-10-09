package pro.sketchware.designer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;

import org.junit.Test;

import java.util.List;

public class DesignerTest {
    /** Generated code must be valid Java 7, the default language level of projects. */
    private static CompilationUnit parseJava7(String source) {
        ParserConfiguration configuration = new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_7);
        ParseResult<CompilationUnit> result = new JavaParser(configuration).parse(source);
        assertTrue(result.getProblems().toString() + "\n" + source, result.isSuccessful());
        return result.getResult().orElseThrow();
    }

    private static final String TABLES = """
            # notes app
            table Note
              title text
              done bool
              rating real
              created int
              picture blob

            table Tag
              label string
            """;

    @Test
    public void parsesTables() {
        DesignSpec.Parsed<DesignSpec.Table> parsed = DesignSpec.parseTables(TABLES);
        assertTrue(parsed.errors().toString(), parsed.ok());
        assertEquals(2, parsed.items().size());
        assertEquals(5, parsed.items().get(0).fields().size());
        assertEquals(FieldType.BOOL, parsed.items().get(0).fields().get(1).type());
        assertEquals(FieldType.TEXT, parsed.items().get(1).fields().get(0).type());
    }

    @Test
    public void tableErrorsHaveLineNumbers() {
        DesignSpec.Parsed<DesignSpec.Table> parsed = DesignSpec.parseTables("title text\ntable select\ntable Note\n  id int\n  x color\n  y int\n  y text\n");
        List<String> errors = parsed.errors();
        assertTrue(errors.get(0).startsWith("Line 1:"));
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("Line 2:")));    // reserved word
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("Line 4:")));    // id is added for you
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("Line 5:") && e.contains("color")));
        assertTrue(errors.stream().anyMatch(e -> e.startsWith("Line 7:") && e.contains("twice")));
        assertFalse(parsed.ok());
    }

    @Test
    public void emptyTableIsAnError() {
        assertTrue(DesignSpec.parseTables("table A\ntable B\n  x int").errors().contains("Table A has no fields"));
        assertFalse(DesignSpec.parseTables("").ok());
    }

    @Test
    public void sqliteHelperIsValidJava7() {
        List<DesignSpec.Table> tables = DesignSpec.parseTables(TABLES).items();
        Generated file = SqliteGenerator.generate("com.my.app", "AppDatabase", "app.db", 1, tables);
        assertEquals("AppDatabase.java", file.fileName());
        CompilationUnit unit = parseJava7(file.source());
        assertTrue(unit.getClassByName("AppDatabase").isPresent());
        String source = file.source();
        assertTrue(source.contains("CREATE TABLE Note (id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, done INTEGER, rating REAL, created INTEGER, picture BLOB)"));
        assertTrue(source.contains("public long insertNote(Note item)"));
        assertTrue(source.contains("public List<Tag> getTagList(String orderBy)"));
        assertTrue(source.contains("values.put(\"done\", item.done ? 1 : 0);"));
        assertTrue(source.contains("item.done = cursor.getInt(cursor.getColumnIndexOrThrow(\"done\")) != 0;"));
        assertTrue(source.contains("\"id = ?\""));    // values are bound, never concatenated
    }

    @Test
    public void sqliteChecks() {
        List<DesignSpec.Table> tables = DesignSpec.parseTables(TABLES).items();
        assertNotNull(SqliteGenerator.check("com.my.app", "Note", "app.db", 1, tables));
        assertNotNull(SqliteGenerator.check("com.my.app", "Db", "my db", 1, tables));
        assertNotNull(SqliteGenerator.check("com.1my", "Db", "app.db", 1, tables));
        assertNotNull(SqliteGenerator.check("com.my.app", "Db", "app.db", 0, tables));
        assertNull(SqliteGenerator.check("com.my.app", "Db", "app.db", 2, tables));
    }

    @Test
    public void firebaseModelsAreValidJava7AndRefuseBlobs() {
        List<DesignSpec.Table> tables = DesignSpec.parseTables(TABLES).items();
        assertNotNull(FirebaseModelGenerator.check("com.my.app", tables));
        List<DesignSpec.Table> noBlob = DesignSpec.parseTables("table User\n name text\n age int\n score real\n admin bool").items();
        List<Generated> files = FirebaseModelGenerator.generate("com.my.app", noBlob);
        assertEquals("User.java", files.get(0).fileName());
        String source = files.get(0).source();
        parseJava7(source);
        assertTrue(source.contains("public HashMap<String, Object> toMap()"));
        assertTrue(source.contains("item.age = ((Number) map.get(\"age\")).longValue();"));
    }

    @Test
    public void parsesEndpoints() {
        DesignSpec.Parsed<DesignSpec.Endpoint> parsed = DesignSpec.parseEndpoints("""
                GET users /users
                GET user /users/{id}
                POST addUser /users body
                put renameUser /users/{id}/name/{name} body
                DELETE removeUser /users/{id}
                """);
        assertTrue(parsed.errors().toString(), parsed.ok());
        assertEquals(List.of("id", "name"), parsed.items().get(3).pathParams());
        assertEquals(DesignSpec.Method.PUT, parsed.items().get(3).method());
        assertTrue(parsed.items().get(2).hasBody());
    }

    @Test
    public void endpointErrors() {
        List<String> errors = DesignSpec.parseEndpoints("""
                GET users users
                PATCH edit /x body
                GET withBody /x body
                FETCH x /x
                GET a /a/{class}
                GET dup /a
                GET dup /b
                GET shutdown /c
                """).errors();
        assertTrue(errors.get(0).startsWith("Line 1:"));
        assertTrue(errors.get(1).contains("PATCH"));
        assertTrue(errors.get(2).contains("GET request cannot have a body"));
        assertTrue(errors.get(3).contains("FETCH"));
        assertTrue(errors.get(4).contains("{class}"));
        assertTrue(errors.get(5).startsWith("Line 7:"));
        assertTrue(errors.get(6).startsWith("Line 8:"));
    }

    @Test
    public void restClientIsValidJava7() {
        List<DesignSpec.Endpoint> endpoints = DesignSpec.parseEndpoints("GET user /users/{id}\nPOST addUser /users body\nGET search /search?q={query}").items();
        Generated file = RestClientGenerator.generate("com.my.app", "Api", "https://api.example.com/", endpoints);
        String source = file.source();
        parseJava7(source);
        assertTrue(source.contains("public static final String BASE_URL = \"https://api.example.com\";"));
        assertTrue(source.contains("public void user(String id, Callback callback)"));
        assertTrue(source.contains("send(\"GET\", \"/users/\" + encode(id), null, callback);"));
        assertTrue(source.contains("public void addUser(String body, Callback callback)"));
        assertTrue(source.contains("\"/search?q=\" + encode(query)"));
    }

    @Test
    public void pathExpressions() {
        assertEquals("\"/a\"", RestClientGenerator.pathExpression("/a"));
        assertEquals("\"/a/\" + encode(id) + \"/b\"", RestClientGenerator.pathExpression("/a/{id}/b"));
        assertEquals("\"/\" + encode(a) + encode(b)", RestClientGenerator.pathExpression("/{a}{b}"));
    }

    @Test
    public void restChecks() {
        List<DesignSpec.Endpoint> endpoints = DesignSpec.parseEndpoints("GET a /a").items();
        assertNotNull(RestClientGenerator.check("com.my.app", "Api", "ftp://x", endpoints));
        assertNotNull(RestClientGenerator.check("com.my.app", "a", "https://x", endpoints));
        assertNull(RestClientGenerator.check("com.my.app", "Api", "https://x.org/v1", endpoints));
    }

    @Test
    public void identifiersAndQuoting() {
        assertTrue(Identifiers.isJavaName("_x1"));
        assertFalse(Identifiers.isJavaName("class"));
        assertFalse(Identifiers.isSqlSafe("order"));
        assertTrue(Identifiers.isPackageName("com.my.app"));
        assertFalse(Identifiers.isPackageName("com..app"));
        assertEquals("\"a\\\"b\\\\c\\n\"", Identifiers.quote("a\"b\\c\n"));
    }
}
