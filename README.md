# RiktigRefactor.Java

[![Build and test](https://github.com/RiktigRefactor/RiktigRefactor.Java/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/RiktigRefactor/RiktigRefactor.Java/actions/workflows/build-and-test.yml)
![Status: early development](https://img.shields.io/badge/status-early%20development-orange)

Behavior-preserving refactorings for Java, built on the JDK's compiler API (`javax.tools` and `com.sun.source`).
*Riktig* (Swedish/Norwegian) means correct.
When the tool cannot prove a transformation is safe, it refuses with a clear reason instead of guessing.

This is the Java implementation of the [RiktigRefactor specification](https://github.com/RiktigRefactor/riktigrefactor.spec).

<!-- toc -->
## Contents

  * [What it can do](#what-it-can-do)
    * [Rename](#rename)
    * [Extract IIFE](#extract-iife)
    * [Make method static](#make-method-static)
    * [Move static member](#move-static-member)
    * [Convert static method to instance method](#convert-static-method-to-instance-method)
  * [Using it](#using-it)
  * [Java version](#java-version)
  * [Building and testing](#building-and-testing)<!-- endToc -->

## What it can do

The tool has these refactorings:

### Rename

Rename renames a symbol and every reference to it across the workspace.

#### Before

<!-- snippet: rename-before -->
<a id='snippet-rename-before'></a>
```java
class Order {
    int qty;

    int total() {
        return qty * 2;
    }
}
```
<sup><a href='/src/test/java/io/github/riktigrefactor/java/docs/ReadmeExamples.java#L9-L18' title='Snippet source file'>snippet source</a> | <a href='#snippet-rename-before' title='Start of snippet'>anchor</a></sup>
<!-- endSnippet -->

#### After

<!-- snippet: rename-after -->
<a id='snippet-rename-after'></a>
```java
class Order {
    int quantity;

    int total() {
        return quantity * 2;
    }
}
```
<sup><a href='/src/test/java/io/github/riktigrefactor/java/docs/ReadmeExamples.java#L22-L31' title='Snippet source file'>snippet source</a> | <a href='#snippet-rename-after' title='Start of snippet'>anchor</a></sup>
<!-- endSnippet -->

See more details in [docs/rename.md](docs/rename.md).

### Extract IIFE

Extract IIFE wraps a selected expression, or whole statements of one block, in an immediately-invoked lambda.

#### Before

<!-- snippet: extract-iife-before -->
<a id='snippet-extract-iife-before'></a>
```java
int total = price * qty + 1;
```
<sup><a href='/src/test/java/io/github/riktigrefactor/java/docs/ReadmeExamples.java#L36-L38' title='Snippet source file'>snippet source</a> | <a href='#snippet-extract-iife-before' title='Start of snippet'>anchor</a></sup>
<!-- endSnippet -->

#### After

<!-- snippet: extract-iife-after -->
<a id='snippet-extract-iife-after'></a>
```java
int total = ((java.util.function.IntBinaryOperator) (priceArg, qtyArg) -> priceArg * qtyArg)
                .applyAsInt(price, qty)
        + 1;
```
<sup><a href='/src/test/java/io/github/riktigrefactor/java/docs/ReadmeExamples.java#L45-L49' title='Snippet source file'>snippet source</a> | <a href='#snippet-extract-iife-after' title='Start of snippet'>anchor</a></sup>
<!-- endSnippet -->

See more details in [docs/extract-iife.md](docs/extract-iife.md).

### Make method static

Make method static turns an instance method into a static method of the same class that takes the receiver as its first parameter.

#### Before

<!-- snippet: make-method-static-before -->
<a id='snippet-make-method-static-before'></a>
```java
class Order {
    int price;

    int total(int qty) {
        return price * qty;
    }
}

int use(Order order) {
    int t = order.total(3);
    return t;
}
```
<sup><a href='/src/test/java/io/github/riktigrefactor/java/docs/ReadmeExamples.java#L55-L69' title='Snippet source file'>snippet source</a> | <a href='#snippet-make-method-static-before' title='Start of snippet'>anchor</a></sup>
<!-- endSnippet -->

#### After

<!-- snippet: make-method-static-after -->
<a id='snippet-make-method-static-after'></a>
```java
class Order {
    int price;

    static int total(Order order, int qty) {
        return order.price * qty;
    }
}

int use(Order order) {
    int t = Order.total(order, 3);
    return t;
}
```
<sup><a href='/src/test/java/io/github/riktigrefactor/java/docs/ReadmeExamples.java#L73-L87' title='Snippet source file'>snippet source</a> | <a href='#snippet-make-method-static-after' title='Start of snippet'>anchor</a></sup>
<!-- endSnippet -->

See more details in [docs/make-method-static.md](docs/make-method-static.md).

### Move static member

Move static member moves a static method or field to another class or record in the workspace, and rewrites every reference to name the target.

#### Before

<!-- snippet: move-static-member-before -->
<a id='snippet-move-static-member-before'></a>
```java
class Order {
    int price;

    static int total(Order order, int qty) {
        return order.price * qty;
    }
}

class Pricing {}

int use(Order order) {
    int t = Order.total(order, 3);
    return t;
}
```
<sup><a href='/src/test/java/io/github/riktigrefactor/java/docs/ReadmeExamples.java#L91-L107' title='Snippet source file'>snippet source</a> | <a href='#snippet-move-static-member-before' title='Start of snippet'>anchor</a></sup>
<!-- endSnippet -->

#### After

<!-- snippet: move-static-member-after -->
<a id='snippet-move-static-member-after'></a>
```java
class Order {
    int price;
}

class Pricing {
    static int total(Order order, int qty) {
        return order.price * qty;
    }
}

int use(Order order) {
    int t = Pricing.total(order, 3);
    return t;
}
```
<sup><a href='/src/test/java/io/github/riktigrefactor/java/docs/ReadmeExamples.java#L111-L127' title='Snippet source file'>snippet source</a> | <a href='#snippet-move-static-member-after' title='Start of snippet'>anchor</a></sup>
<!-- endSnippet -->

See more details in [docs/move-static-member.md](docs/move-static-member.md).

### Convert static method to instance method

Convert static method to instance method is the inverse of Make method static: a parameter of a static method becomes the receiver.

#### Before

<!-- snippet: convert-to-instance-method-before -->
<a id='snippet-convert-to-instance-method-before'></a>
```java
class Order {
    int price;
}

class Pricing {
    static int total(Order order, int qty) {
        return order.price * qty;
    }
}

int use(Order order) {
    int t = Pricing.total(order, 3);
    return t;
}
```
<sup><a href='/src/test/java/io/github/riktigrefactor/java/docs/ReadmeExamples.java#L131-L147' title='Snippet source file'>snippet source</a> | <a href='#snippet-convert-to-instance-method-before' title='Start of snippet'>anchor</a></sup>
<!-- endSnippet -->

#### After

<!-- snippet: convert-to-instance-method-after -->
<a id='snippet-convert-to-instance-method-after'></a>
```java
class Order {
    int price;

    int total(int qty) {
        return price * qty;
    }
}

class Pricing {}

int use(Order order) {
    int t = order.total(3);
    return t;
}
```
<sup><a href='/src/test/java/io/github/riktigrefactor/java/docs/ReadmeExamples.java#L151-L167' title='Snippet source file'>snippet source</a> | <a href='#snippet-convert-to-instance-method-after' title='Start of snippet'>anchor</a></sup>
<!-- endSnippet -->

See more details in [docs/convert-static-method-to-instance-method.md](docs/convert-static-method-to-instance-method.md).

## Using it

```shell
riktig-java rename --workspace . --file src/main/java/shop/Order.java --at 12:17 --new-name total
```

See [the command line](docs/command-line.md) for the options, exit codes and refusal codes.

## Java version

The tool targets Java 25, the newest JDK available where it is developed, and analyzes code as the running JDK's compiler does.
The specification asks for the latest stable language version; moving to it is planned.

## Building and testing

```shell
./build-and-test
```

For more information, see [CONTRIBUTING.md](CONTRIBUTING.md).
