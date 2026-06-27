# OMS Examples Module

This module contains runnable examples and demo applications. It is separated from the main project code to avoid pulling demo-only dependencies into the primary build.

Quick start (run locally using embedded H2):

```powershell
cd D:\projects\java\omplatform\oms-examples
mvn spring-boot:run
```

Then POST to http://localhost:18080/example/create?name=test

To enable Seata integration you need a running Seata TC and enable `seata.enabled=true` in `application.yml` or via `-Dspring.profiles.active` and add Seata server configuration.

