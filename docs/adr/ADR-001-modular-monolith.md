# ADR-001: Modular monolith

Status: Accepted. One deployable Spring Boot runtime is used for the first slice. Domain modules communicate through published facades and events. Direct imports of another module's internal packages or repositories are prohibited.
