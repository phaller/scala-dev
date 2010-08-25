This is an experimental branch of the Scala compiler and standard library that contains a few additions to provide even better support for embedded DSLs (we call that *language virtualization*).

The key features are as follows:

- overloadable while-loops, if-then-else statements, etc. (not only for-comprehensions)
- extension methods: define new infix-methods on existing types (pimp-my-library with less boilerplate)
- transparent proxies: re-route all method calls on these proxy objects to a forwarder method

Further descriptions and examples will follow.