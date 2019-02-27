# Akka snippet with OAuth2 Bearer

## Summary

This snippet use the g8 [akka-http-quickstart](https://github.com/akka/akka-http-quickstart-scala.g8) template and use 
OAuth2 baerer to authenticate.

The list of tokens are given in the `application.conf` file in `src/main/resources` folder.

Run the server with `sbt run` and try:

```
curl -H "Authorization: Bearer ABCD" http://localhost:8080/users
```

To post multiple csv files :
```
curl -v -F csv=@read.csv -F csv=@read2.csv http://localhost:8080/upload
```
