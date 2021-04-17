# Design document for the implementation of `j.u.regex.*`

Java and JavaScript have different support for regular expressions.
In addition to Java having many more features, they also *differ* in the specifics of most of the features they have in common.

For performance and code size reasons, we still want to use the native JavaScript `RegExp` class.
Modern JavaScript engines JIT-compile `RegExp`s to native code, so it is impossible to compete with that using a user-space engine.

Therefore, our strategy for `java.util.regex` is to *compile* Java regexes down to JavaScript regexes that behave in the same way.
The compiler is in the file `PatternCompiler.scala`, and is invoked at the time of `Pattern.compile()`.

We can deal with most features in a compliant way using that strategy, while retaining performance, and without sacrificing code size too much compared to directly passing regexes through without caring about the discrepancies.
There are however a few features that are either never supported, or only supported when targeting a recent enough version of ECMAScript.

## Support

The set of supported features depends on the target ECMAScript version, specified in `ESFeatures.esVersion`.

The following features are never supported:

* the `CANON_EQ` flag,
* the `\X`, `\b{g}` and `\N{...}` expressions,
* `\p{name}` character classes representing Unicode *blocks*,
* the `\G` boundary matcher, *except* if it appears at the very beginning of the regex (e.g., `\Gfoo`),
* embedded flag expressions with inner groups, i.e., constructs of the form `(?idmsuxU-idmsuxU:X)`,
* embedded flag expressions without inner groups, i.e., constructs of the form `(?idmsuxU-idmsuxU)`, *except* if they appear at the very beginning of the regex (e.g., `(?i)abc` is accepted, but `ab(?i)c` is not), and
* numeric "back" references to groups that are defined later in the pattern (note that even Java does not support *named* back references like that).

The following features require `esVersion >= ESVersion.ES2015`:

* the `UNICODE_CASE` flag.

The following features require `esVersion >= ESVersion.ES2018`:

* the `MULTILINE` and `UNICODE_CHARACTER_CLASS` flags,
* look-behind assertions `(?<=X)` and `(?<!X)`,
* the `\b` and `\B` expressions used together with the `UNICODE_CASE` flag,
* `\p{name}` expressions where `name` is not one of the [POSIX character classes](https://docs.oracle.com/en/java/javase/15/docs/api/java.base/java/util/regex/Pattern.html#posix).

It is worth noting that, among others, the following features *are* supported in all cases, even when no equivalent feature exists in ECMAScript at all, or in the target version of ECMAScript:

* correct handling of surrogate pairs (natively supported in ES 2015+),
* the `\G` boundary matcher when it is at the beginning of the pattern (corresponding to the 'y' flag, natively supported in ES 2015+),
* named groups and named back references (natively supported in ES 2018+),
* the `DOTALL` flag (natively supported in ES 2018+),
* ASCII case-insensitive matching (`CASE_INSENSITIVE` on but `UNICODE_CASE` off),
* comments with the `COMMENTS` flag,
* POSIX character classes in ASCII mode, or their Unicode variant with `UNICODE_CHARACTER_CLASS` (if the latter is itself supported, see above),
* complex character classes with unions and intersections (e.g., `[a-z&&[^g-p]]`),
* atomic groups `(?>X)`,
* possessive quantifiers `X*+`, `X++` and `X?+`,
* the `\A`, `\Z` and `\z` boundary matchers,
* the `\R` expression,
* embedded quotations with `\Q` and `\E`, both outside and inside character classes.

In addition, among others, the following features have the correct semantics from Java, even though their corresponding feature in JavaScript has different semantics:

* the `^` and `$` boundary matchers with the `MULTILINE` flag (when the latter is supported),
* the predefined character classes `\h`, `\s`, `\v`, `\w` and their negated variants, respecting the `UNICODE_CHARACTER_CLASS` flag,
* the `\b` and `\B` boundary matchers, respecting the `UNICODE_CHARACTER_CLASS` flag,
* the internal format of `\p{name}` character classes, including the `\p{javaMethodName}` classes,
* octal escapes and control escapes.

### Guarantees

If a feature is not supported, a `PatternSyntaxException` is thrown at the time of `Pattern.compile()`.

If `Pattern.compile()` succeeds, the regex is guaranteed to behave exactly like on the JVM, *except* for capturing groups within repeated segments (both for their back references and subsequent calls to `group`, `start` and `end`):

* on the JVM, a capturing group always captures whatever substring was successfully matched last by *that* group during the processing of the regex:
  - even if it was in a previous iteration of a repeated segment and the last iteration did not have a match for that group, or
  - if it was during a later iteration of a repeated segment that was subsequently backtracked;
* in JS, capturing groups within repeated segments always capture what was matched (or not) during the last iteration that was eventually kept.

The behavior of JavaScript is more "functional", whereas that of the JVM is more "imperative".
This imperative nature is also reflected in the `hitEnd()` and `requireEnd()` methods of `Matcher`, which we do not support (they don't link).

## Implementation strategy

Java regexes are compiled to JS regexes in one pass, using a recursive descent approach in the companion class.
There is a state variable `pIndex` which indicates the position inside the original `pattern`.
Compilation methods parse a subexpression at `pIndex`, advance `pIndex` past what they parsed, and return the result of the compilation.
Results are of three possible types:

* `String` for a compiled JS regexp subexpression,
* `Int` for a code point, or
* `CompiledCharClass` for a pre-compiled character class that can be inserted as a subexpression or in a `[]` character class.

Parsing is always done at the code point level, and not at the individual `Char` level.

### State

In addition to `pIndex`, the following state is maintained during compilation:

* `originalGroupCount`: the number of capturing groups (named and unnamed) from the original pattern for which a group number has been allocated so far,
* `compiledGroupCount`: the number of capturing groups of the eventual compiled pattern for which a group number has been allocated so far,
* `groupNumberMap`: a mapping from original group number to the corresponding group numbers in the compiled pattern,
* `namedGroups`, a mapping from original group name to the corresponding *original* group number.

Usually, there is a 1-to-1 relationship between original group numbers and compiled groups numbers. However, differences are introduced when compiling atomic groups and possessive quantifiers.

We never produce named groups in the compiled patterns, even in ES 2018+, so there is no map of compiled group names.

### JS RegExp flags and case sensitivity

Irrespective of the Java flags, we always use the following JS flags when they are supported (including through dynamic detection):

- 'u' for correct handling of surrogate pairs and Unicode rules for case folding (introduced in ES2015),
- 's' for the dotAll behavior, i.e., `.` matches any code point (introduced in ES2018).

In addition, we use the 'i' JS flag when both `CASE_INSENSITIVE` and `UNICODE_CASE` are on.
Since `UNICODE_CASE` is only supported in ES 2015+, this implies that 'u' is supported, and hence that we can leave all the handling of case insensitivity to the native RegExp.

When `CASE_INSENSITIVE` is on but `UNICODE_CASE` is off, we must apply ASCII case insensitivity.
This is not supported by JS RegExps, so we implement it ourselves during compilation.
This is represented by the property `asciiCaseInsensitive`.
When it is true:

* any single code point that is an ASCII letter, such as 'g', is compiled to a character class with the uppercase and lowercase variants (e.g., `[Gg]`), in subexpressions or in character classes, and
* any character range in a character class that intersects with the range `A-Z` and/or `a-z` is compiled with additional range(s) to cover the uppercase and lowercase variants.

`PatternCompiler` never uses any other JS RegExp flag.
`Pattern` adds the 'g' flag for its general-purpose instance of `RegExp` (the one used for everything except `Matcher.matches()`), as well as the 'y' flag if the regex is sticky and it is supported.

### Entry point

The entry point is `PatternCompiler#compile()`, which:

1. Processes an embedded flag expression as the beginning of the pattern,
2. Validates the flags,
3. Processes an initial `\G` boundary matcher to record the pattern as sticky, and
4. Calls the main compilation method `compileTopLevelOrInsideGroup(insideGroup = false)`.

The latter is recursive for `()`-enclosed groups, and flat for other subexpressions.

We first describe the compilation with the assumption that the underlying `RegExp`s support the `u` flag.
This is always true in ES 2015+, and dynamically determined at run-time in ES 5.1.
We will cover later what happens when it is not supported.

### Main "control structures"

The following constructs are translated as is:

* Sequences and alternatives,
* Greedy quantifiers of the form `X*`, `X+` and `X?`,
* Lazy quantifiers of the form `X*?`, `X+?` and `X??`,
* Nameless capturing groups `(X)`, after updating the group counts and group number map,
* Non-capturing groups and look-ahead groups of the form `(?:X)`, `(?=X)` and `(?!X)`,
* Look-behind groups of the form `(?<=X)` and `(?<!X)`, after validating that they are supported.

Named capturing groups are compiled as nameless ones.
The mapping from their name to their allocated group number is recorded in `namedGroups`.

Atomic groups and possessive quantifiers are handled specially and will be discussed later.

### Single code points

The following subexpressions represent a single, fixed code point:

* Any code point except `\ ( ) [ { . ^ $ |` (note that this includes `]` and `}`)
* `\0n`, `\0nn` and `\0mnn`: octal escape
* `\xhh` and `\x{h...h}`: hexadecimal escape
* `\uhhhh\uhhhh` where the first escape represents a high surrogate and the second one represents a low surrogate: treated as a single escape for a single supplementary code point
* `\uhhhh` in other situations: Unicode escape for a BMP code point (including unpaired surrogates)
* `\a`, `\t`, `\n`, `\f`, `\r`, and `\e`, representing U+0007, U+0009, U+000A, U+000B, U+000D and U+001B, respectively
* `\cx` where `x` is a code point, representing `x ^ 0x40` (this is not well documented in the JavaDoc, but is explained in [a StackOverflow question](https://stackoverflow.com/questions/35208570/java-regular-expression-cx-control-characters) and experimentally verified)
* `\x` where `x` is any code point except an ASCII letter or digit, representing `x` itself (this includes standard escapes like `\\`, `\.`, etc.)

Single code point expressions are compiled irrespective of how they were represented in the source pattern.
This is implemented in `def literal(cp: Int): String`.

* If a single code point `x` is one of `^ $ \ . * + ? ( ) [ ] { } |`, it is compiled to `\x`.
* If it is a low surrogate, it is compiled to `(?:x)`.
* Otherwise, it is compiled to `x`.

Note that `x` is a 1-Char string if it is a BMP code point, or a 2-Char string if it is a supplementary code point.

For example:

* `a` is compiled to `a`
* `\x67` is compiled to `C`
* `\x24` is compiled to `\$`
* `\n` is compiled to a new line character, i.e., the Scala expression `0x000A.toChar` (not to the 2-Char string `"\\n"`)
* `\uDD1E` is compiled to the Scala expression `"(?:" + 0xDD1E.toChar + ")"` because it is a low surrogate
* `\uD834\uDD1E` is compiled to `𝄞`, i.e., the 2-char string `"" + 0xD834.toChar + 0xDD1E.toChar`
* `\uD834\uD834` is not even parsed as a single code point, but as two separate subexpressions, because the second one is not a low surrogate

The wrapping of low surrogates with `(?:x)` is there to ensure that we do not artificially create a surrogate pair in the compiled pattern where none existed in the source pattern.
Consider the source pattern `\x{D834}\x{DD1E}`, for example.
If low surrogates were not wrapped, it would be compiled to a surrogate pair, which would match the input string `"𝄞"` although it is not supposed to.

### Quotes

A quote starts with `\Q`, and ends at the first occurrence of `\E` or the end of the string.
The full string in between is taken as a sequence of code points.

Each code point is compiled as described in "Single code points" for `def literal(cp: Int)`, and the compiled patterns are concatenated in a sequence.
This is implemented in `def literal(s: String)`.

### The dot `.`

A `.` represents "any" code point, except some kinds of new lines, depending on the `DOTALL` and `UNIX_LINES` flags.
Since JavaScript's `.`'s interpretation of new lines is not the same as Java's, we compile `.` to custom character classes:

- with `DOTALL`: compiled to `.` if there is native support for the 's' flag (recall that it is always used when supported), or `[\d\D]` otherwise.
- without `DOTALL` but with `UNIX_LINES`: compiled to `[^\n]` (but with an NL character instead of the two characters `\` and `n`).
- without `DOTALL` nor `UNIX_LINES`: compiled to `[^\n\r\u0085\u2028\u2029]` (again, where those escapes are actually preprocessed as single Chars).

### Predefined character classes

Predefined character classes represent a set of code points that matches a single code point in the input string.
The set typically depends on the value of `UNICODE_CHARACTER_CLASS`.

Since virtually none of them has a corresponding predefined character class in JS RegExps, they are all compiled as custom `[...]` character classes, according to their definition.

For example:

| Java pattern            | without `UNICODE_CHARACTER_CLASS` | with `UNICODE_CHARACTER_CLASS`                                    |
|-------------------------|-----------------------------------|-------------------------------------------------------------------|
| `\d`                    | `[0-9]`                           | `\p{Nd}`                                                          |
| `\W`                    | `[^a-zA-Z_0-9]`                   | `[^\p{Alphabetic}\p{Mn}\p{Me}\p{Mc}\p{Nd}\p{Pc}\p{Join_Control}]` |
| `\p{Print}`             | `[ -~]`                           | `[^\p{Zl}\p{Zp}\p{Cc}\p{Cs}\p{Cn}]`                               |
| `\p{javaLetterOrDigit}` | `[\p{L}\p{Nd}]`                   | `[\p{L}\p{Nd}]`                                                   |

Predefined character classes are stored as instances of `CompiledCharClass` in predefined constants and maps.
A `CompiledCharClass` preserves enough structure to also allow good integration in custom `[]` character classes.
The maps contain all the possible values for `\p{name}` *except* all the Unicode Scripts and Blocks (but it does contain all the Unicode character categories).
Scripts are normalized on the fly (because Java allows them to be case-insensitive but JS requires the correct casing).
Unicode blocks are not supported.

To create a negative character class of the form `[^X]`, we use the function `codePointNotAmong`, which handles the special cases for

* X being an empty set (notably for `.` with `DOTALL`, see above), and
* handling surrogate pairs when the 'u' flag is not supported (see below).

### Predefined assertions

The assertion `^` is compiled to

- `(?:^)` without `MULTILINE` (the wrapping is necessary in case it ends up being repeated, because that is not syntactically valid in JS, although it is valid once wrapped in a group)
- `(?<=^|\n)` with `MULTILINE` and `UNIX_LINES`
- `(?<=^|\r(?!\n)|[\n\u0085\u2028\u2029])` with `MULTILINE` but without `UNIX_LINES`

Note that the latter two use look-behind assertions in JavaScript, requiring support for ES2018+.
We cannot use the 'm' flag of JavaScript `RegExp`s because its semantics differ from the Java ones (either with or without `UNIX_LINES`).

The assertion `$` is compiled in a similar way.

The assertions `\b` and `\B` are compiled as is if both `UNICODE_CASE` and `UNICODE_CHARACTER_CLASS` are false.
This is correct because:

- since `UNICODE_CHARACTER_CLASS` is false, word chars are considered to be `[a-zA-Z_0-9]` for Java semantics, and
- since `UNICODE_CASE` is false, we do not use the 'i' flag in the JS RegExp, and so word chars are considered to be `[a-zA-Z_0-9]` for the JS semantics as well.

In all other cases, we determine the compiled form of `\w` and `\W` and use a custom look-around-based implementation. This requires ES2018+, hence why we go to the trouble of trying to reuse `\b` and `\B` if we can.

The other predefined assertions are compiled as follows:

- `\A` is always compiled to `(?:^)` (note that we never use the 'm' flag in the JS RegExp)
- `\z` is always compiled to `(?:$)`
- `\Z` with `UNIX_LINES` is compiled to `(?=\n?$)`
- `\Z` without `UNIX_LINES` is compiled to `(?=(?:\r\n?|[\n\u0085\u2028\u2029])?$)`

### Linebreak matcher

`\R` is compiled to its definition wrapped in a non-capturing group, i.e., to `(?:\r\n|[\n\u000B\u000C\r\u0085\u2028\u2029])` (but where the escapes are preprocessed as single Chars, as usual).

### Back references

After parsing, back references are replaced with a numeric back reference that corresponds in the compiled pattern.
This is done using the state in `groupNumberMap` and `namedGroups`.
Since named groups are compiled away as unnamed ones, named back references are also compiled to numeric back references.

The back references are wrapped in `(?:\1)`, in case they happen to be followed by an ASCII digit code point.

### Atomic groups

Atomic groups are not well documented in the JavaDoc, but they are well covered in outside documentation such as [on Regular-Expressions.info](https://www.regular-expressions.info/atomic.html).
They have the form `(?>X)`.
An atomic group matches whatever `X` matches, but does not backtrack in the middle.
It is all-or-nothing.

JS does not support atomic groups.
However, there exists a trick to implement atomic groups on top of look-ahead groups and back references, including with the correct performance characterics.
It is well documented in the article [Mimicking Atomic Groups](https://blog.stevenlevithan.com/archives/mimic-atomic-groups).
In a nutshell, we compile `(?>X)` to `(?:(?=(X))\N)` where `N` is the allocated group number for the capturing group `(X)`.

This introduces a discrepancy between the original group numbers and the compiled group numbers for any subsequent capturing group.
This is why we maintain `groupNumberMap`.
Note that the discrepancy applies within `X` as well, so we record it before compiling the subexpression `X`.

### Possessive quantifiers

[Possessive quantifiers](https://www.regular-expressions.info/possessive.html) can be interpreted as sugar for atomic groups over greedy quantifiers.
For example, `X*+` is equivalent to `(?>X*)`.

Since JS does not support possessive quantifiers any more than atomic groups, we compile them as that desugaring, followed by the compilation scheme of atomic groups.

However, there is an additional problem.
For atomic groups, we know before parsing `X` that we need to record a discrepancy in the group numbering.
For possessive quantifiers, we only know that *after* having parsed `X`, but it should apply also *within* `X`.
We do that with postprocessing.
Before compiling any token `X`, we record the current `compiledGroupCount`, and when compiling a possessive quantifier, we increment the compiled group number of those greater than the recorded count.
We do this

- in the values of `groupNumberMap`, and
- in the back references found in the compiled pattern for `X`.

The latter is pretty ugly, but is robust nevertheless.

### Custom character classes

Custom character classes of the form `[...]` are very complicated.
Java supports intersections and nested character classes, which virtually no other regular expression system supports.
We have to compile all of those away.

The parsing itself is already a bit involved, but not fundamentally complicated.
As usual, we parse code points as indivisible units.

Escapes for individual code points are handled as in "Single code points" (with the same parsing method, actually).
Instead of being protected with `(?:X)`, low surrogates are moved to the beginning of any set of code points.
This is fine because elements in sets can be reordered at will.
The set of code points that are escaped with a `\` is also different (it is `] \ - ^`) and is handled in `literalCodePoint`.

Ranges of single codepoints are treated in the same way, with ranges whose starting code point is a low surrogate being moved to the beginning.

Escapes for predefined character classes are also parsed as in "Predefined character classes", but they are kept under their `CompiledCharClass` form.

`\Q..\E` escapes are processed in a similar way, and expand to a set of single code points (rather than a *sequence* of single code points as is the case outside character classes).

With that out of the way, there are two main challenges left:

* `CompiledCharClass` that represent negative classes, such as `\S` which represents `[^\t-\r ]`, and
* Unions and intersections with nested character classes.

When you consider nested negated character classes containing predefined character classes that represent negated classes themselves, the problem can seem extremely complex.
Because of Unicode character classes, it is not possible to just *simplify* complex nested character classes somehow into a single character class at compile time.
So we must compile down the unions, intersections and negations.

Conceptually, we decompose complex character classes as follows.
*A*, *B*, and *C* stand for any sequence of character class content without negation nor intersection, including potentially empty strings.
*\c* stands for any predefined character class, including `\p{name}` ones.

Note that the `^` has a special meaning only right after a `[`.
It negates the entire character class.

We get rid of intersections using the following equivalences:

* `[A&&...&&B&&C]` is equivalent to `(?=[A])...(?=[B])[C]`
* `[^A&&...&&B&&C]` is equivalent to `[^A]|...|[^B]|[^C]`

Other complex constructs are dealt with as follows:

* `[A\cB]` is in general equivalent to `[A[C]B]` where `C` is the expansion of the predefined character class `\c`, but we can simplify it as `[ACB]` if `C` is not of the form `^...`
* `[^A\cB]` is in general equivalent to `[^A[C]B]` where `C` is the expansion of the predefined character class `\c`, but we can simplify it as `[^ACB]` if `C` is not of the form `^...`
* `[ABC]` is equivalent to `[B]|[AC]`
* `[^ABC]` is equivalent to `(?![B])[^AC]`
* `[[A]]` is equivalent to `[A]`
* `[[^A]]` is equivalent to `[^A]`

Using those rewrite rules, we can always reduce a complex character class so that it does not contain any union, intersection or predefined character class.

For example,

* `[^a\dc]` expands to `[^a0-9c]` in ASCII mode, or `[^a\p{Nd}c]` in Unicode mode
* `[a-z&&[^m-p]]` expands to `(?=[a-z])[^m-p]`
* `[^2\D5]` expands to `[^2\P{Nd}5]` in Unicode mode, or `[^2[^0-9]5]` in ASCII mode, which then gives `(?![^0-9])[^25]`
* `[d-l[o-t].-?&&f[k-q] -Z&&1-3\D]` expands to `(?=[d-l[o-t].-?])(?=[f[k-q] -Z])[1-3\D]`, then to `(?=[o-t]|[d-l.-?])(?=[k-q]|[f -Z])(?:[^0-9]|[1-3])`
* `[^d-l[o-t].-?&&f[k-q] -Z&&1-3\D]` expands to `[^d-l[o-t].-?]|[^f[k-q] -Z]|[^1-3\D]`, then to `(?:(?![o-t])[^d-l.-?])|(?:(?![k-q])[^f -Z])|(?:(?![^0-9])[^1-3])`

This is implemented in `compileCharacterClass()`, which uses a `CharacterClassState` and its methods to keep track of the parsing and compilation state.
While the above description is declarative, `compileCharacterClass()`'s implementation is quite intricate to perform all the above rewritings in a single pass, rather than having to analyze and rewrite in a loop.

### Handling surrogate pairs without support for the 'u' flag

So far, we have assumed that the underlying RegExp supports the 'u' flag, which we test with `supportsUnicode`.
In this section, we cover how the compilation is affected when it is not supported.
This can only happen when we target ES 5.1.

Without support for the 'u' flag, the JavaScript RegExp engine will parse the pattern and process the input with individual Chars rather than code points.
In other words, it will consider surrogate pairs as two separate (and therefore separable) code units.
If we do nothing against it, it can jeopardize the semantics of regexes in several ways:

* a `.` will match only the high surrogate of a surrogate pair instead of the whole codepoint,
* same issue with any negative character class like `[^a]`,
* an unpaired high surrogate in the pattern may match the high surrogate of a surrogate pair in the input, although it must not,
* a surrogate pair in a character class will be interpreted as *either* the high surrogate or the low surrogate, instead of both together,
* etc.

Since even regexes that contain only ASCII characters and have no flags are affected by some of these issues, we cannot simply declare those features as unsupported.
It would mean that virtually no regex would be correct in ECMAScript 5.1.
Therefore, we go to great lengths to implement the right semantics despite the lack of support for 'u'.

#### Overall idea of the solution

When `supportsUnicode` is false, we apply the following changes to the compilation scheme.
In general, we make sure that:

* something that can match a high surrogate does not match one followed by a low surrogate,
* something that can match a supplementary code point or a high surrogate never selects the high surrogate if it could match the whole code point.

We do nothing special for low surrogates, as all possible patterns go from left to right (we don't have look-behinds in this context) and we otherwise make sure that all code points from the input are either fully matched or not at all.
Therefore, the "cursor" of the engine can never stop in the middle of a code point, and so low surrogates are only visible if they were unpaired to being with.
The only exception to this is when the cursor is at the beginning of the pattern, when using `find`.
In that case we cannot a priori prevent the JS engine from trying to find a match starting in the middle of a code point.
To address that, we have special a posteriori handling in `Pattern.execFind()`.

#### Concretely

A single code point that is a high surrogate `x` is compiled to `(?:x(?![L]))`, where `L` is `\uDC00-\uDFFF`, the range of all low surrogates.
The negative look-ahead group prevents a match from separating the high surrogate from a following low surrogate.

A dot-all (in `codePointNotAmong("")`) is compiled to `(?:[^H]|[H](?:[L]|(?![L])))`, where `H` is `\uD800-\uDBFF`, the range of all high surrogates.
This means either

* any code unit that is not a high surrogate, or
* a high surrogate and a following low surrogate (taking a full code point is allowed), or
* a high surrogate that is not followed by a low surrogate (separating a surrogate pair is not allowed).

We restrict the internal contract of `codePointNotAmong(s)` to only take BMP code points that are not high surrogates, and compile it to the same as the dot-all but with the characters in `s` excluded like the high surrogates: `(?:[^sH]|[H](?:[L]|(?![L])))`.

Since `UNICODE_CHARACTER_CLASS` is not supported, all but one call site of `codePointNotAmong` already respect that stricter contract.
The only one that does not is the call `codePointNotAmong(thisSegment)` inside `CharacterClassState.finishConjunct()`.
To make that one compliant, we make sure not to add illegal characters in `thisSegment`.
To do that, we exploit the rewrites for `[ABC]` and `[^ABC]` where `B` is an illegal character to isolate it in separate alternatives, that we can compile as a single code point above.

In code point ranges, the story is much more intricate, but we can still achieve what we need by decomposing the range into smaller ones:

* one with only BMP code points below high surrogates
* one with high surrogates
* one with BMP code points above high surrogates
* one with supplementary code points, which is further decomposed if necessary

More details can be found in `addCodePointRange`.

## About code size

For historical reasons, code size is critical in this class.
Before Scala.js 1.6.0, `java.util.regex.Pattern` was just a wrapper over native `RegExp`s.
The patterns were passed through with minimal preprocessing, without caring about the proper semantics.
This created an expectation of small code size for this class.
When we fixed the semantics, we had to introduce this compiler, which is non-trivial.
In order not to regress too much on code size, we went to great lengths to minimize the code size impact of this class, in particular in the default ES 2015 configuration.

When modifying this code, make sure to preserve as small a code size as possible.
