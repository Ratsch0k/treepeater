package treepeater;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import treepeater.pathnormalization.DynamicPathNormalizer;

class DynamicPathNormalizerTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("singleSegmentCases")
    void classifySegment(String input, String expected) {
        assertEquals(expected, DynamicPathNormalizer.normalizeSegment(input));
    }

    private static Stream<Arguments> singleSegmentCases() {
        return Stream.of(
                Arguments.of("2", ":id"),
                Arguments.of("2048", ":id"),
                Arguments.of("550e8400-e29b-41d4-a716-446655440000", ":uuid"),
                Arguments.of("{550e8400-e29b-41d4-a716-446655440000}", ":uuid"),
                Arguments.of("507f1f77bcf86cd799439011", ":objectId"),
                Arguments.of("d41d8cd98f00b204e9800998ecf8427e", ":hash"),
                Arguments.of("356a192b7913b04c54574d18c28d46e6395428ab", ":hash"),
                Arguments.of("01ARZ3NDEKTSV4RRFFQ69G5FAV", ":ulid"),
                Arguments.of("2024-01-15", ":date"),
                Arguments.of("20240115", ":date"),
                Arguments.of("2024-01", ":date"),
                Arguments.of("2024-01-15T10:30:00Z", ":timestamp"),
                Arguments.of("2024-01-15T10:30:00+02:00", ":timestamp"),
                Arguments.of("v1", "v1"),
                Arguments.of("v2", "v2"),
                Arguments.of("v2beta", "v2beta"),
                Arguments.of("api", "api"),
                Arguments.of("users", "users"),
                Arguments.of("utf-8", "utf-8"),
                Arguments.of("en-US", "en-US"),
                Arguments.of("1.2.3", "1.2.3"),
                Arguments.of("main.css", "main.css"),
                Arguments.of("12345.json", "12345.json"),
                Arguments.of("user-123", "user-123"),
                Arguments.of("my-awesome-post-2024-edition", "my-awesome-post-2024-edition"),
                Arguments.of("admin@example.com", "admin@example.com"),
                Arguments.of(":id", ":id"),
                Arguments.of("$metadata", "$metadata"),
                Arguments.of("$count", "$count"),
                Arguments.of("$batch", "$batch"),
                Arguments.of("ResetData()", "ResetData()"));
    }

    @Test
    void numericDateOrderingUsesDateNotId() {
        assertEquals(":date", DynamicPathNormalizer.normalizeSegment("20240115"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("oDataCases")
    void oDataSegments(String input, String expected) {
        assertEquals(expected, DynamicPathNormalizer.normalizeSegment(input));
    }

    private static Stream<Arguments> oDataCases() {
        return Stream.of(
                Arguments.of("Products(1)", "Products(:id)"),
                Arguments.of("Customers('ALFKI')", "Customers(:id)"),
                Arguments.of("Products(guid'0e8adfae-9e6b-4941-92b7-20bf6e95c560')", "Products(:id)"),
                Arguments.of("Customers('A,B')", "Customers(:id)"),
                Arguments.of("Products(ID=1)", "Products(ID=:id)"),
                Arguments.of(
                        "OrderDetails(OrderID=10248,ProductID=11)",
                        "OrderDetails(OrderID=:orderid,ProductID=:productid)"),
                Arguments.of("GetNearestAirport(lat=33,lon=-118)", "GetNearestAirport(lat=:lat,lon=:lon)"));
    }

    @Test
    void multiSegmentDatePath() {
        List<String> input = List.of("blog", "2024", "01", "15", "title");
        List<String> expected = List.of("blog", ":year", ":month", ":day", "title");
        assertEquals(expected, DynamicPathNormalizer.normalize(input));
    }

    @Test
    void fullPathNormalization() {
        List<String> input = List.of("users", "2", "status");
        List<String> expected = List.of("users", ":id", "status");
        assertEquals(expected, DynamicPathNormalizer.normalize(input));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("idempotentCases")
    void idempotent(List<String> segments) {
        List<String> once = DynamicPathNormalizer.normalize(segments);
        List<String> twice = DynamicPathNormalizer.normalize(once);
        assertEquals(once, twice);
    }

    private static Stream<List<String>> idempotentCases() {
        return Stream.of(
                List.of("users", "2", "status"),
                List.of("Products(ID=:id)"),
                List.of("OrderDetails(OrderID=:orderid,ProductID=:productid)"),
                List.of("blog", "2024", "01", "15", "title"));
    }

    @Test
    void idempotentSingleSegments() {
        for (String segment : List.of(
                "Products(ID=:id)",
                "OrderDetails(OrderID=:orderid,ProductID=:productid)",
                "2",
                ":id")) {
            assertEquals(
                    DynamicPathNormalizer.normalizeSegment(segment),
                    DynamicPathNormalizer.normalizeSegment(
                            DynamicPathNormalizer.normalizeSegment(segment)));
        }
    }

    @Test
    void normalizeNullAndEmpty() {
        assertEquals(List.of(), DynamicPathNormalizer.normalize(List.of()));
        assertEquals(List.of(), DynamicPathNormalizer.normalize(null));
    }

    @Test
    void matrixParamsStrippedBeforeClassification() {
        assertEquals(":id", DynamicPathNormalizer.normalizeSegment("2;version=1"));
        assertEquals("shop;jsessionid=abc", DynamicPathNormalizer.normalizeSegment("shop;jsessionid=abc"));
    }
}
