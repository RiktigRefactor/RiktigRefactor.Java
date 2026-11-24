package io.github.riktigrefactor.java.docs;

/** Compiled so that the examples in README.md are known to be valid Java. */
@SuppressWarnings("unused")
final class ReadmeExamples {
    private ReadmeExamples() {}

    static final class RenameBefore {
        // begin-snippet: rename-before
        class Order {
            int qty;

            int total() {
                return qty * 2;
            }
        }

        // end-snippet
    }

    static final class RenameAfter {
        // begin-snippet: rename-after
        class Order {
            int quantity;

            int total() {
                return quantity * 2;
            }
        }

        // end-snippet
    }

    static final class ExtractIifeBefore {
        int use(int price, int qty) {
            // begin-snippet: extract-iife-before
            int total = price * qty + 1;
            // end-snippet
            return total;
        }
    }

    static final class ExtractIifeAfter {
        int use(int price, int qty) {
            // begin-snippet: extract-iife-after
            int total = ((java.util.function.IntBinaryOperator) (priceArg, qtyArg) -> priceArg * qtyArg)
                            .applyAsInt(price, qty)
                    + 1;
            // end-snippet
            return total;
        }
    }

    static final class MakeMethodStaticBefore {
        // begin-snippet: make-method-static-before
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

        // end-snippet
    }

    static final class MakeMethodStaticAfter {
        // begin-snippet: make-method-static-after
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

        // end-snippet
    }

    static final class MoveStaticMemberBefore {
        // begin-snippet: move-static-member-before
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

        // end-snippet
    }

    static final class MoveStaticMemberAfter {
        // begin-snippet: move-static-member-after
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

        // end-snippet
    }

    static final class ConvertToInstanceMethodBefore {
        // begin-snippet: convert-to-instance-method-before
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

        // end-snippet
    }

    static final class ConvertToInstanceMethodAfter {
        // begin-snippet: convert-to-instance-method-after
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

        // end-snippet
    }
}
