fun foo(l: List<String>){}
fun foo(l: List<String>, p: Int){}

fun foo(s: String?){}

fun bar(o: Any) {
    foo(o as <caret>)
}

// EXIST: { lookupString:"List", itemText:"List<String>" }
// EXIST: { lookupString:"String", itemText:"String" }
// NOTHING_ELSE
