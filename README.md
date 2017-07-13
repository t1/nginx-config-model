Java model classes for NGINX config files


# Goals

* POJO style
* Read-Modify-Write
* Change only what is known, esp.:
    * Don't change unknown tokens
    * Don't change comments or whitespace

# Non-Goals

* Validation
* To Be Fast
* Access to all aspects of the config
* Build config from scratch

# Alternatives

* [odiszapc/nginx-java-parser](https://github.com/odiszapc/nginx-java-parser)
    * Doesn't retain empty lines
    * Not typesafe
