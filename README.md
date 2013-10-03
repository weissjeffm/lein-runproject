# lein-runproject 

A [Leiningen](https://github.com/technomancy/leiningen) plugin for running a leiningen project without having to check it out from source, or download a binary. It will include all dependencies (downloading them if necessary) and run the project's `:main` namespace. Based partly on lein-try plugin.

## Usage

#### Leiningen ([via Clojars](https://clojars.org/lein-runproject))

Put the following into the `:plugins` vector of the `:user` profile in your `~/.lein/profiles.clj`:

```clojure
[lein-runproject "LATEST"] 
```

##### Example

```clojure
{:user {:plugins [[lein-runproject "LATEST"]]}}
```
#### Command Line

You can use `lein-runproject` run any project, any version that exists in your profile's repositories.

```bash
$ lein runproject fooproj 0.5.1 arg1 arg2 ...
Fetching dependencies... (takes a while the first time)

[Program output]

# or if you don't care what version
$ lein runproject fooproj LATEST arg1 arg2 ...
Fetching dependencies... (takes a while the first time)

[Program output]

```

To see available options, call `lein help runproject`.

## License

Copyright &copy; 2013 Jeff Weiss

Distributed under the Eclipse Public License, the same as Clojure.

