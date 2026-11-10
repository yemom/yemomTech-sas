# YemomBizGrow

### AI-Powered Business Growth Platform for Ethiopian Businesses

**YemomBizGrow** is a multi-tenant business growth platform designed to help Ethiopian businesses establish their digital presence, improve their online visibility, manage customer relationships, monitor marketing performance, and make better business decisions using data-driven insights.

The platform brings essential digital business tools into one workspace, combining website management, SEO auditing, CRM, analytics, marketing campaigns, AI-powered assistance, agency collaboration, reporting, and subscription management.

<p align="center">

**Built for Ethiopian businesses. Designed for measurable growth.**

</p>

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Platform Modules](#platform-modules)
- [Technology Stack](#technology-stack)
- [System Architecture](#system-architecture)
- [Security and Multi-Tenancy](#security-and-multi-tenancy)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Environment Configuration](#environment-configuration)
- [Running with Docker](#running-with-docker)
- [Testing and Quality Assurance](#testing-and-quality-assurance)
- [API Documentation](#api-documentation)
- [Ethiopian Market Localization](#ethiopian-market-localization)
- [Deployment](#deployment)
- [Project Documentation](#project-documentation)
- [Development Principles](#development-principles)
- [Project Status](#project-status)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

Many small and medium-sized businesses in Ethiopia face challenges managing their digital presence, understanding website performance, generating leads, tracking marketing activities, and making informed business decisions.

YemomBizGrow aims to address these challenges through a unified digital business growth platform.

Rather than requiring businesses to depend on multiple disconnected tools, the platform provides an organization-based workspace where business owners and their teams can manage important digital growth activities.

### Core Objectives

- Help businesses understand and improve their website performance.
- Identify technical and on-page SEO problems through automated website audits.
- Organize customer information, leads, and sales opportunities.
- Connect website traffic data with marketing and business activities.
- Provide meaningful insights through AI-assisted analysis.
- Support collaboration between businesses and digital marketing agencies.
- Make digital business management more accessible to Ethiopian businesses.

### Target Users

| User Group | Primary Use |
|---|---|
| Small and medium-sized businesses | Manage digital growth activities |
| Business owners | Monitor business performance and reports |
| Marketing teams | Manage campaigns, leads, and analytics |
| Digital marketing agencies | Manage and collaborate with multiple clients |
| Sales teams | Track prospects and customer pipelines |
| Business administrators | Manage organizations, members, and permissions |

---

## Key Features

### 1. Organization and Workspace Management

- Organization registration and onboarding.
- Organization-based workspaces.
- Organization profile and settings management.
- Team member invitations and membership management.
- Role-based permissions.
- Organization activity and audit records.
- Organization-specific website registration.

### 2. Website Management and SEO Auditing

- Register and manage business websites.
- Automated website crawling and HTML analysis.
- Technical SEO checks.
- On-page SEO analysis.
- Mobile-related SEO checks.
- SEO issue categorization and scoring.
- Audit history and recommendations.

### 3. Customer Relationship Management (CRM)

- Lead registration and management.
- Customer contact information.
- Lead status and sales pipeline management.
- Pipeline value tracking in Ethiopian Birr.
- Public lead capture form.
- Lead organization and follow-up workflows.

### 4. Website Analytics

- Google OAuth integration.
- Google Analytics 4 (GA4) connectivity.
- Google Search Console integration.
- Website-specific Google property selection.
- Daily traffic information.
- Device breakdown.
- Top-performing pages.
- Analytics synchronization status.

External Google account access and valid API configuration are required for live analytics data.

### 5. AI-Powered Business Assistance

- AI-generated business recommendations.
- Deterministic offline recommendation capabilities.
- Configurable business chatbot.
- Public chatbot API.
- Embeddable chatbot integration.
- Business-context-aware assistance.

The AI architecture supports an offline provider and an optional external AI provider. External AI functionality requires the appropriate API configuration.

AI-generated information is intended to support decision-making rather than independently execute business operations.

### 6. Marketing Campaign Management

- Marketing campaign creation and management.
- Multi-channel campaign tracking.
- UTM parameter support.
- Campaign attribution.
- Cost per lead (CPL) calculations.
- Return on advertising spend (ROAS) calculations.
- Campaign performance monitoring.

Supported campaign channels include Telegram, Google Ads, Meta, TikTok, email, and SMS.

### 7. Agency and Client Collaboration

- Agency organization management.
- Client organization connection requests.
- Client-controlled approval and rejection.
- Agency-client relationship management.
- Access-level restrictions.
- Client workspace information.
- Cross-organization business summaries.

Agency access levels include:

- `FULL`
- `READ_ONLY`
- `MARKETING_ONLY`

Client organizations control the approval and authorization of agency access.

### 8. Business Reports

- Consolidated business information.
- SEO performance summaries.
- Website traffic summaries.
- CRM and lead summaries.
- Marketing campaign performance.
- CSV report export.

### 9. Subscription and Payment Management

- Subscription plan management.
- Billing dashboard.
- Payment transaction records.
- Chapa payment gateway integration.
- Server-side payment verification.
- Webhook signature validation.
- Ethiopian payment context, including Telebirr and CBE Birr through supported Chapa checkout methods.

Real payment processing requires valid Chapa credentials and provider configuration. Development-mode simulation must not be treated as a real payment.

### 10. Ethiopian Localization

- Ethiopian Birr (ETB) currency support.
- Ethiopia-oriented phone number validation.
- Ethiopian phone number normalization.
- 15% VAT calculation support.
- `Africa/Addis_Ababa` timezone.
- Ethiopian business-oriented industry categories.

---

## Platform Modules

The application follows a modular architecture, with each module responsible for a specific business capability.

| Module | Responsibility |
|---|---|
| Authentication | Registration, login, tokens, sessions |
| User Management | User account information |
| Organization | Workspace and business organization management |
| Membership | Roles, permissions, and member management |
| Invitations | Team invitation workflows |
| Website | Website registration and management |
| SEO | Website crawling, audits, and recommendations |
| CRM | Lead management and sales pipeline |
| Google Integration | OAuth, GA4, and Search Console |
| AI | Business recommendations and chatbot functionality |
| Marketing | Campaign management and attribution |
| Agency | Agency-client collaboration |
| Reports | Business reporting and CSV export |
| Billing | Subscription and payment management |
| Audit | Organization activity and security-related records |

*Module availability and integration completeness can differ. Some capabilities require external services, credentials, or additional deployment configuration.*

---

## Technology Stack

YemomBizGrow uses a modern full-stack architecture with Java and Spring Boot powering the backend and Next.js powering the frontend.

### Frontend

| Technology | Purpose |
|---|---|
| Next.js 16 | React application framework |
| React 19 | User interface development |
| TypeScript | Static type checking |
| Tailwind CSS 4 | Styling and responsive UI |
| TanStack Query | Server-state and API data management |
| React Hook Form | Form management |
| Zod | Data validation |
| Vitest | Frontend unit testing |
| Playwright | Browser end-to-end testing |

### Backend

| Technology | Purpose |
|---|---|
| Java 21 | Backend programming language |
| Spring Boot 3.5 | Application framework |
| Spring Security | Authentication and authorization |
| Spring Data JPA | Database persistence |
| Hibernate | ORM |
| Spring Validation | Request validation |
| JWT | Token-based authentication |
| Flyway | Database schema migrations |
| Jsoup | HTML parsing and website analysis |
| Spring Mail | Email delivery |
| Spring Actuator | Health and monitoring endpoints |
| Springdoc OpenAPI | API documentation |

### Database and Infrastructure

| Technology | Purpose |
|---|---|
| PostgreSQL 17 | Primary relational database |
| Redis 7 | Login throttling and supporting infrastructure |
| Docker | Containerization |
| Docker Compose | Local development environment |
| GitHub Actions | CI/CD automation |
| Maven | Backend dependency and build management |
| npm | Frontend dependency management |

### External Integrations

- Google OAuth 2.0
- Google Analytics 4
- Google Search Console
- Chapa Payment Gateway
- OpenAI-compatible AI provider
- SMTP email service

---

## System Architecture

YemomBizGrow follows a **modular monolith architecture**.

The backend is organized into independent business modules within a single Spring Boot application. This keeps deployment and development manageable while maintaining clear module responsibilities.

### High-Level Architecture

```text
                 USERS
                   |
                   v
          NEXT.JS FRONTEND
          React 19 + TypeScript
                   |
              REST API
                   |
                   v
          SPRING BOOT BACKEND
                Java 21
                   |
        +----------+----------+
        |          |          |
        v          v          v
   AUTH/RBAC   BUSINESS     INTEGRATION
              MODULES       MODULES
        |          |          |
        +----------+----------+
                   |
          +--------+--------+
          |                 |
          v                 v
      PostgreSQL           Redis
      Database             Cache/
                           Throttling
```

### Architecture Characteristics

- Modular monolith backend.
- RESTful API communication.
- Organization-based multi-tenancy.
- Layered backend structure.
- Repository and service separation.
- JWT-based authentication.
- Database migrations managed by Flyway.
- Centralized exception handling.
- External service integration through dedicated components.
- Docker-based development and deployment.

### Multi-Tenant Data Model

The platform uses a shared-database multi-tenant approach.

Organizations operate within the same application and database infrastructure, while their business data is isolated through organization-level ownership and authorization checks.

```text
                    PLATFORM
                       |
          +------------+------------+
          |                         |
          v                         v
     ORGANIZATION A            ORGANIZATION B
          |                         |
     +----+-----+              +----+-----+
     |    |     |              |    |     |
   Users Websites Leads      Users Websites Leads
     |    |     |              |    |     |
     +----+-----+              +----+-----+
```

Organization membership and permissions determine which resources a user can access.

---

## Security and Multi-Tenancy

Security is a core part of the platform architecture.

### Authentication

- JWT-based authentication.
- Short-lived access tokens.
- Refresh token rotation.
- Refresh-token reuse detection.
- Secure refresh-cookie handling.
- CSRF protection for cookie-based operations.
- Password validation and BCrypt hashing.
- Login throttling.

### Authorization

- Role-based access control.
- Organization membership verification.
- Permission-based backend authorization.
- Organization-scoped resource access.
- Restricted agency-client access.
- Protection against unauthorized organization operations.

### Data Protection

- PostgreSQL persistence.
- Flyway-controlled database migrations.
- Encrypted storage for supported external integration credentials.
- Environment-based secret configuration.
- Audit logging for important business operations.

### Website Crawler Security

The SEO crawler includes security controls designed to reduce Server-Side Request Forgery (SSRF) risks.

These include:

- Private and loopback IP restrictions.
- DNS destination validation.
- Connection-time destination pinning.
- Redirect destination revalidation.
- TLS hostname verification.

Security controls should continue to be tested as the application evolves. Their presence does not replace independent penetration testing or production security review.

---

## Project Structure

```text
yemomTech-sas/
│
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   │   └── com/
│   │   │   │       └── YemomBizGrow/
│   │   │   │           ├── auth/
│   │   │   │           ├── user/
│   │   │   │           ├── organization/
│   │   │   │           ├── membership/
│   │   │   │           ├── invitation/
│   │   │   │           ├── website/
│   │   │   │           ├── seo/
│   │   │   │           ├── crm/
│   │   │   │           ├── integration/
│   │   │   │           ├── ai/
│   │   │   │           ├── marketing/
│   │   │   │           ├── agency/
│   │   │   │           ├── reports/
│   │   │   │           ├── billing/
│   │   │   │           ├── audit/
│   │   │   │           └── common/
│   │   │   │
│   │   │   └── resources/
│   │   │       ├── db/migration/
│   │   │       └── application.yml
│   │   │
│   │   └── test/
│   │
│   └── pom.xml
│
├── frontend/
│   ├── src/
│   │   ├── app/
│   │   ├── components/
│   │   ├── hooks/
│   │   ├── lib/
│   │   ├── services/
│   │   └── types/
│   │
│   ├── e2e/
│   ├── public/
│   └── package.json
│
├── docs/
│   ├── adr/
│   ├── SYSTEM_ARCHITECTURE.md
│   ├── DATABASE_DESIGN.md
│   ├── API_SPECIFICATION.md
│   ├── AUTHENTICATION_AND_SECURITY.md
│   ├── MULTI_TENANCY.md
│   ├── DEVELOPMENT_SETUP.md
│   ├── DEPLOYMENT.md
│   ├── TESTING.md
│   └── ROADMAP.md
│
├── infrastructure/
│   └── docker/
│       ├── backend.Dockerfile
│       └── frontend.Dockerfile
│
├── .github/
│   └── workflows/
│
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## Getting Started

Follow these instructions to run the project locally.

### Prerequisites

Install the following tools:

| Requirement | Recommended Version |
|---|---|
| Java JDK | 21 |
| Node.js | 22 LTS |
| npm | Included with Node.js |
| Docker Desktop | Current version |
| Git | Current version |
| PostgreSQL | 17, if not using Docker |
| Redis | 7, if not using Docker |

### 1. Clone the Repository

```bash
git clone https://github.com/yemom/yemomTech-sas.git

cd yemomTech-sas
```

### 2. Configure Environment Variables

Create your local environment file.

**Windows PowerShell:**

```powershell
Copy-Item .env.example .env
```

Open `.env` and configure the required local database, security, and application settings.

Do not commit your real `.env` file.

### 3. Start PostgreSQL and Redis

The repository provides Docker Compose configuration for local infrastructure.

```bash
docker compose up -d postgres redis
```

This starts:

| Service | Local Address |
|---|---|
| PostgreSQL | `localhost:5632` |
| Redis | `localhost:6637` |

Wait until both services become healthy before starting the backend.

### 4. Run the Backend

Open a terminal:

```powershell
cd backend
```

Start Spring Boot using the Maven wrapper:

```powershell
$env:SERVER_PORT="8082"
.\mvnw.cmd spring-boot:run
```

The development backend is expected at:

```text
http://localhost:8082
```

Health endpoint:

```text
http://localhost:8082/actuator/health
```

### 5. Run the Frontend

Open another terminal:

```powershell
cd frontend
```

Install dependencies:

```bash
npm ci
```

Configure the frontend API URL in `frontend/.env.local`:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8082/api/v1
```

Start the frontend:

```bash
npm run dev
```

Open:

```text
http://localhost:3001
```

You should now be able to access the development application.

---

## Environment Configuration

The project uses environment variables to separate application configuration from source code.

Important configuration variables include:

| Variable | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE` | Spring application profile |
| `SERVER_PORT` / `APP_PORT` | Backend server port, depending on launch configuration |
| `DATABASE_URL` | PostgreSQL connection URL |
| `DATABASE_USERNAME` | Database username |
| `DATABASE_PASSWORD` | Database password |
| `REDIS_HOST` | Redis hostname |
| `REDIS_PORT` | Redis port |
| `JWT_SECRET` | JWT signing secret |
| `ENCRYPTION_KEY` | Encryption key for supported credentials |
| `CORS_ALLOWED_ORIGINS` | Allowed frontend origins |
| `FRONTEND_URL` | Frontend application URL |
| `GOOGLE_CLIENT_ID` | Google OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | Google OAuth client secret |
| `GOOGLE_REDIRECT_URI` | Google OAuth callback URL |
| `CHAPA_SECRET_KEY` | Chapa payment API secret |
| `CHAPA_WEBHOOK_SECRET` | Chapa webhook verification secret |
| `OPENAI_API_KEY` | Optional external AI provider key |
| `MAIL_HOST` | SMTP server |
| `MAIL_PORT` | SMTP server port |
| `MAIL_USERNAME` | SMTP username |
| `MAIL_PASSWORD` | SMTP password |

Not every integration is required to start the core application. Google, Chapa, email, and external AI features require their respective provider configurations.

**Important:** Replace example credentials and development-only secrets before deployment. Never expose private credentials through `NEXT_PUBLIC_*` variables.

---

## Running with Docker

For a complete local containerized environment:

```bash
docker compose up --build
```

The Compose configuration starts:

- PostgreSQL
- Redis
- Spring Boot backend
- Next.js frontend

Expected local addresses:

| Service | Address |
|---|---|
| Frontend | `http://localhost:3000` |
| Backend | `http://localhost:8080` |
| Backend health | `http://localhost:8080/actuator/health` |
| PostgreSQL | `localhost:5632` |
| Redis | `localhost:6637` |

To stop the containers:

```bash
docker compose down
```

To stop the containers and remove local database volumes:

```bash
docker compose down -v
```

**Warning:** The last command deletes local database and Redis volume data. The provided Compose configuration is intended for development, not direct production deployment.

---

## Testing and Quality Assurance

YemomBizGrow includes backend and frontend testing infrastructure.

### Backend Tests

The backend uses:

- JUnit 5
- Mockito
- AssertJ
- Spring Boot Test
- Testcontainers
- PostgreSQL and Redis integration testing

Run backend verification:

**Windows:**

```powershell
cd backend

.\mvnw.cmd -B verify
```

**Linux/macOS:**

```bash
cd backend

./mvnw -B verify
```

### Frontend Tests

Run type checking:

```bash
cd frontend

npm run typecheck
```

Run linting:

```bash
npm run lint
```

Run unit tests:

```bash
npm run test
```

Create a production build:

```bash
npm run build
```

### Browser End-to-End Tests

The frontend uses Playwright for browser-based testing.

Install Chromium:

```bash
npx playwright install chromium
```

Start the backend and frontend development servers, then run:

```bash
npm run e2e
```

The E2E suite covers important user journeys and includes tests for areas such as authentication, CRM, billing navigation, agency workflows, and multi-tenant isolation.

### Continuous Integration

GitHub Actions automates important quality checks, including:

- Backend verification.
- Frontend checks.
- Backend Docker image build.
- Frontend Docker image build.

The latest repository security-remediation report records a successful four-job CI run. Browser tests and live third-party integrations should be verified separately rather than assumed to have passed merely because Docker builds and CI jobs are green.

---

## API Documentation

The backend exposes RESTful API endpoints under:

```text
/api/v1
```

OpenAPI documentation is provided through Springdoc.

During local development, the Swagger UI is available at:

```text
http://localhost:8082/swagger-ui.html
```

The API documentation describes supported endpoints, request structures, response formats, and authentication requirements.

---

## Ethiopian Market Localization

YemomBizGrow is designed with Ethiopian businesses in mind.

Localization considerations include:

- Ethiopian Birr (ETB) for financial values.
- Ethiopia-specific phone number validation.
- Ethiopian business and industry categories.
- 15% VAT calculation support.
- `Africa/Addis_Ababa` timezone.
- Support for Ethiopian payment providers through Chapa's supported payment methods.
- Business workflows relevant to small and medium-sized enterprises.

The platform is designed to make digital business management more practical and accessible in the Ethiopian market.

---

## Deployment

The project supports container-based deployment.

### Frontend

The Next.js application can be deployed using a compatible hosting provider such as Vercel.

### Backend

The Spring Boot application can be deployed using Docker-compatible infrastructure, including Render.

### Database and Cache

Production infrastructure should use managed PostgreSQL and Redis services with private networking and secure credentials.

### Production Checklist

Before deploying:

- Configure production environment variables.
- Generate strong JWT and encryption secrets.
- Configure production database credentials.
- Configure Redis connectivity.
- Set exact allowed CORS origins.
- Configure secure cookies and HTTPS.
- Configure Google OAuth redirect URLs.
- Configure Chapa production credentials and webhook settings.
- Configure SMTP delivery.
- Verify database migrations.
- Run backend and frontend verification.
- Test organization isolation and authorization.
- Test payment and OAuth flows with real provider sandbox credentials.
- Review logs, health checks, backups, and monitoring.
- Perform an independent security review.

Production readiness should be established through deployment testing, security assessment, and real integration verification.

---

## Project Documentation

Additional technical documentation is maintained in the `docs/` directory.

| Document | Description |
|---|---|
| [System Architecture](docs/SYSTEM_ARCHITECTURE.md) | Backend and frontend architecture |
| [Database Design](docs/DATABASE_DESIGN.md) | Database schema and relationships |
| [API Specification](docs/API_SPECIFICATION.md) | API endpoints and contracts |
| [Authentication and Security](docs/AUTHENTICATION_AND_SECURITY.md) | Authentication and security controls |
| [Multi-Tenancy](docs/MULTI_TENANCY.md) | Organization isolation and permissions |
| [Development Setup](docs/DEVELOPMENT_SETUP.md) | Local development configuration |
| [Deployment](docs/DEPLOYMENT.md) | Deployment configuration and procedures |
| [Testing](docs/TESTING.md) | Testing strategy and commands |
| [Roadmap](docs/ROADMAP.md) | Development milestones and implementation status |
| [Architecture Decision Records](docs/adr/) | Important architecture decisions |

---

## Development Principles

The project follows several engineering principles:

1. **Modular architecture:** Keep business capabilities organized into clear backend modules.
2. **Security first:** Enforce authentication, authorization, and tenant isolation on the backend.
3. **Data integrity:** Use database constraints, transactions, and Flyway migrations.
4. **No fabricated business metrics:** Business insights should be based on persisted or verified data.
5. **Responsible AI:** AI should assist users without bypassing authorization or independently performing sensitive business operations.
6. **Maintainability:** Prefer clear service boundaries, reusable components, and understandable code.
7. **Testability:** Cover important business rules, security boundaries, and user workflows with automated tests.
8. **Ethiopian market relevance:** Prioritize local business requirements, currency, payments, and localization.

---

## Project Status

YemomBizGrow is under active development.

The repository contains a substantial implementation across its business modules, security infrastructure, frontend application, database migrations, and deployment workflows.

Some capabilities depend on external provider credentials, production infrastructure, or further end-to-end verification.

The project should therefore be evaluated by individual module implementation and verified behavior rather than treating every documented capability as equally production-ready.

---

## Contributing

Contributions, suggestions, and technical feedback are welcome.

To contribute:

1. Fork the repository.
2. Create a feature branch.
3. Implement your changes.
4. Add or update relevant tests.
5. Run the backend and frontend quality checks.
6. Submit a pull request with a clear description.

Please keep changes consistent with the existing modular architecture and security requirements.

---

## License

See the [LICENSE](LICENSE) file for licensing information.

---

## Repository

**Project:** YemomBizGrow  
**Repository:** https://github.com/yemom/yemomTech-sas

**Built with a focus on helping Ethiopian businesses grow through technology, data, and intelligent digital tools.**
