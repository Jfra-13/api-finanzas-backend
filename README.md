# API Finanzas Backend

![Java](https://img.shields.io/badge/Java-21-blue?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.5-brightgreen?logo=springboot)
![Database](https://img.shields.io/badge/Database-H2%20%2F%20Azure-orange)
![Status](https://img.shields.io/badge/status-active-success)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

## Descripción

API REST para la gestión financiera personal y de negocios. Permite registrar usuarios, autenticarlos con JWT, guardar transacciones, consultar ingresos, calcular cuotas diarias, ver resúmenes y recuperar contraseñas por correo.  
Está pensada para desarrollo local con **H2** y para despliegue en **Azure**. La versión **v0** ya fue publicada allí, y la **v0.5** será la siguiente etapa de integración con un frontend en Kotlin.

## Estado del proyecto

- **Fase 1: Base sólida** → completada al 100%
- **Fase 2: Núcleo del negocio** → en progreso
- **Fase 3: Reportes y analíticas** → en progreso
- **Fase 4: Refactor y calidad** → en progreso
- **v0** → desplegada en Azure
- **v0.5** → próxima integración con frontend Kotlin

## Tecnologías usadas

- Java 21
- Spring Boot 4.0.5
- Spring Web MVC
- Spring Data JPA
- Spring Security
- Spring Validation
- Spring Mail
- JWT (`jjwt`)
- Lombok
- Maven
- H2 local
- Azure

## Requisitos previos

- Java 21
- Maven o Maven Wrapper
- H2 para desarrollo local
- Acceso a Azure si se usa despliegue
- Postman, Insomnia o `pruebas.http`

## Instalación y ejecución

```powershell
git clone https://github.com/Jfra-13/api-finanzas-backend.git
cd api-finanzas-backend
.\mvnw clean install
.\mvnw spring-boot:run
