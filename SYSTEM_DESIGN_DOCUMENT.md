# Application Service - System Design Document

## 1. Overview

The Application Service is a Spring Boot microservice that manages job applications, job postings, saved jobs, and job recommendations for a job seeker platform. It provides RESTful APIs for job seekers and employers to manage their interactions in the job marketplace.

### Technology Stack

- **Framework**: Spring Boot 4.0.2
- **Language**: Java 17
- **Database**: MySQL (configured for PostgreSQL compatibility)
- **Messaging**: Apache Kafka
- **Build Tool**: Maven
- **Key Libraries**:
  - Spring Data JPA (database access)
  - Spring Kafka (message streaming)
  - Spring Web MVC (REST APIs)
  - Spring Validation (request validation)
  - Lombok (boilerplate reduction)
  - Jackson (JSON serialization)

---

## 2. System Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            API Gateway                                   │
│                     (Authentication & Routing)                           │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Application Service                              │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────────┐  │
│  │   Controllers   │  │    Services     │  │     Repositories        │  │
│  │                 │  │                 │  │                         │  │
│  │ - Application  │  │ - Application   │  │ - ApplicationRepository│  │
│  │   Controller   │  │   Service       │  │ - JobRepository         │  │
│  │ - JobManage-   │  │ - JobService    │  │ - SavedJobRepository   │  │
│  │   mentController│ │ - SavedJob     │  │ - RecommendationCache  │  │
│  │                 │  │   Service      │  │   Repository           │  │
│  │                 │  │ - Recommenda-  │  │ - Recommendation-      │  │
│  │                 │  │   tionService  │  │   FeedbackRepository   │  │
│  │                 │  │ - UserProfile  │  │                         │  │
│  │                 │  │   Service      │  │                         │  │
│  └────────┬────────┘  └────────┬────────┘  └────────────┬────────────┘  │
│           │                    │                       │                │
│           └────────────────────┼───────────────────────┘                │
│                                │                                         │
│           ┌────────────────────┴───────────────────────┐                │
│           │              Kafka Messaging               │                │
│           │                                            │                │
│           │  ┌─────────────────┐  ┌────────────────┐  │                │
│           │  │ ProfileEvent    │  │ ProfileEvent  │  │                │
│           │  │ Producer        │  │ Consumer       │  │                │
│           │  │ (Publishing)    │  │ (Listening)    │  │                │
│           │  └─────────────────┘  └────────────────┘  │                │
│           └─────────────────────────────────────────────┘                │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    │               │               │
                    ▼               ▼               ▼
              ┌──────────┐   ┌──────────┐   ┌──────────────┐
              │  MySQL   │   │  Kafka   │   │  User Profile│
              │ Database │   │  Broker  │   │  Service    │
              └──────────┘   └──────────┘   │  (External) │
                                            └──────────────┘
```

### 2.2 API Endpoints

#### Application Controller (`/api/v1/applications`)

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| POST | `/api/v1/applications` | Submit a new job application | Job Seeker |
| GET | `/api/v1/applications` | Get user's applications (paginated) | Job Seeker |
| GET | `/api/v1/applications?jobId={id}` | Get applications for a job | Employer |
| GET | `/api/v1/applications/{id}` | Get application details | Both |
| PUT | `/api/v1/applications/{id}/withdraw` | Withdraw application | Job Seeker |
| PUT | `/api/v1/applications/{id}/status` | Update application status | Employer |
| GET | `/api/v1/applications/stats` | Get application statistics | Job Seeker |
| GET | `/api/v1/applications/{id}/resume` | Download applicant's resume | Employer |

#### Job Management Controller (`/api/v1/jobs`)

| Method | Endpoint | Description | Access |
|--------|----------|-------------|--------|
| POST | `/api/v1/jobs` | Create a new job posting | Employer |
| GET | `/api/v1/jobs` | Get employer's job listings | Employer |
| GET | `/api/v1/jobs/{id}` | Get job details | Public |
| PUT | `/api/v1/jobs/{id}/status` | Update job status | Employer |
| POST | `/api/v1/jobs/{id}/save` | Save a job | Job Seeker |
| DELETE | `/api/v1/jobs/{id}/unsave` | Unsave a job | Job Seeker |
| GET | `/api/v1/jobs/saved` | Get saved jobs | Job Seeker |
| GET | `/api/v1/jobs/saved/count` | Get saved jobs count | Job Seeker |
| GET | `/api/v1/jobs/recommendations` | Get job recommendations | Job Seeker |
| GET | `/api/v1/jobs/recommendations/refresh` | Refresh recommendations | Job Seeker |
| POST | `/api/v1/jobs/recommendations/feedback` | Submit recommendation feedback | Job Seeker |

---

## 3. Data Model

### 3.1 Core Entities

#### Job Entity (`Job.java`)

Represents a job posting created by an employer.

```
Job
├── id: Long (Primary Key, Auto-generated)
├── title: String
├── company: String
├── companyId: String
├── location: String
├── type: String (Full-time, Part-time, Contract, etc.)
├── salary: String
├── description: String (Text)
├── responsibilities: List<String> (Element Collection)
├── skills: List<String> (Element Collection)
├── benefits: List<String> (Element Collection)
├── status: String (Draft, Published, Archived)
├── postedDate: LocalDate
├── employerId: String (FK to Employer)
├── applicationsCount: Integer
├── viewsCount: Integer
├── seniority: String (Entry, Mid, Senior, Lead)
├── logo: String (URL)
├── isRemote: Boolean
├── educationRequired: String
├── createdAt: LocalDateTime
└── updatedAt: LocalDateTime
```

#### Application Entity (`Application.java`)

Represents a job application submitted by a job seeker.

```
Application
├── id: String (UUID)
├── userId: String (FK to User)
├── jobId: Long (FK to Job)
├── status: ApplicationStatus (Enum)
├── appliedDate: LocalDate
├── resumeId: String
├── coverLetter: String (Text)
├── rejectionReason: String
├── withdrawnDate: LocalDateTime
├── withdrawReason: String
├── createdAt: Instant
└── updatedAt: Instant
```

#### SavedJob Entity (`SavedJob.java`)

Represents a job saved by a job seeker for later viewing.

```
SavedJob
├── id: String (UUID)
├── userId: String (FK to User)
├── jobId: Long (FK to Job)
└── savedDate: Instant
```

#### RecommendationCache Entity (`RecommendationCache.java`)

Caches job recommendations for users to improve performance.

```
RecommendationCache
├── id: String (UUID)
├── userId: String
├── jobId: Long
├── matchScore: Integer (0-100)
├── matchReasons: String (semicolon-separated)
├── expiresAt: Instant
└── createdAt: Instant
```

#### RecommendationFeedback Entity (`RecommendationFeedback.java`)

Stores user feedback on job recommendations.

```
RecommendationFeedback
├── id: String (UUID)
├── userId: String
├── jobId: Long
├── feedback: RecommendationFeedbackType (Enum)
├── reason: String
└── createdAt: Instant
```

### 3.2 Enums

#### ApplicationStatus

```
APPLIED → RESUME_VIEWED → IN_REVIEW → SHORTLISTED → INTERVIEW → OFFERED
         ↓                              ↓
       REJECTED ◄───────────────────────┘
       
WITHDRAWN (terminal - only by job seeker)
```

#### RecommendationFeedbackType

- `RELEVANT` - Job is relevant to user's profile
- `NOT_RELEVANT` - Job is not relevant
- `ALREADY_APPLIED` - User already applied
- `NOT_INTERESTED` - User not interested

### 3.3 Database Schema

The database consists of the following tables:

1. **job** - Job postings
2. **job_responsibilities** - Job responsibilities (element collection)
3. **job_skills** - Required skills (element collection)
4. **job_benefits** - Job benefits (element collection)
5. **applications** - Job applications
6. **saved_jobs** - Saved job listings
7. **recommendation_cache** - Cached recommendations
8. **recommendation_feedback** - User feedback on recommendations

---

## 4. Key Services

### 4.1 ApplicationService

Manages job applications throughout their lifecycle.

**Key Methods:**
- `submitApplication()` - Submit a new application
- `getUserApplications()` - Get paginated user applications
- `getApplicationsByJobId()` - Get applications for a job (employer)
- `getApplicationById()` - Get application details
- `withdrawApplication()` - Withdraw an application
- `updateStatus()` - Update application status (employer)
- `getApplicationStats()` - Get application statistics
- `validateStatusTransition()` - Validate status changes

**Key Features:**
- Prevents duplicate applications
- Validates status transitions
- Publishes Kafka events on application changes
- Fetches applicant details from User Profile Service

### 4.2 JobService

Manages job postings.

**Key Methods:**
- `createJob()` - Create a new job posting
- `getJobsByEmployer()` - Get employer's jobs (paginated)
- `getJobById()` - Get job by ID
- `updateJobStatus()` - Update job status

### 4.3 SavedJobService

Manages saved jobs for users.

**Key Methods:**
- `saveJob()` - Save a job
- `unsaveJob()` - Remove saved job
- `getSavedJobs()` - Get saved jobs (paginated)
- `getSavedJobsCount()` - Get saved jobs count

### 4.4 RecommendationService

Provides job recommendations based on user profiles.

**Key Methods:**
- `fetchUserProfile()` - Fetch user profile from Auth Service
- `calculateRecommendations()` - Calculate match scores
- `getRecommendations()` - Get cached recommendations
- `refreshRecommendations()` - Refresh recommendations
- `recordFeedback()` - Record user feedback

**Recommendation Algorithm:**

The recommendation engine uses a weighted scoring system:

| Factor | Weight | Description |
|--------|--------|-------------|
| Skills Matching | 40% | Match user's skills against job requirements |
| Experience Level | 20% | Match user's years of experience to job seniority |
| Education | 20% | Match user's education to job requirements |
| Location Preference | 15% | Match location or remote preference |
| Recency | 10% | Bonus for recently posted jobs |

**Minimum threshold**: 30% match score

### 4.5 UserProfileService

Integrates with external User Profile Service.

**Key Methods:**
- `getUserProfile()` - Fetch user profile
- `getUserName()` - Get user's name
- `getUserEmail()` - Get user's email
- `getResumeUrl()` - Get resume download URL

---

## 5. Event-Driven Architecture

### 5.1 Kafka Topics

#### Application Events (`application-events`)

Published when application state changes:

| Event Type | Trigger |
|------------|---------|
| `APPLICATION_SUBMITTED` | Job seeker submits application |
| `APPLICATION_WITHDRAWN` | Job seeker withdraws application |
| `APPLICATION_STATUS_UPDATED` | Employer updates status |

**Event Payload:**
```json
{
  "eventType": "APPLICATION_SUBMITTED",
  "applicationId": "uuid",
  "jobId": 123,
  "jobTitle": "Software Engineer",
  "companyName": "Tech Corp",
  "companyId": "company-uuid",
  "employerId": "employer-uuid",
  "userId": "user-uuid",
  "applicantName": "John Doe",
  "applicantEmail": "john@example.com",
  "resumeId": "resume-uuid",
  "status": "APPLIED",
  "appliedDate": "2026-02-15",
  "timestamp": "2026-02-15T17:34:52"
}
```

#### Profile Changes (`profile-changes`)

Consumed when user updates their profile:

| Event Type | Entity |
|------------|--------|
| `SKILL_ADDED/UPDATED/DELETED` | SKILL |
| `EXPERIENCE_ADDED/UPDATED/DELETED` | EXPERIENCE |
| `EDUCATION_ADDED/UPDATED/DELETED` | EDUCATION |

### 5.2 Profile Event Consumer

The consumer listens to profile changes and triggers recommendation recalculation:

1. Receives profile change event
2. Validates event type (SKILL, EXPERIENCE, EDUCATION)
3. Fetches updated user profile
4. Gets all active jobs
5. Recalculates recommendations
6. Updates recommendation cache

---

## 6. Security & Authentication

### 6.1 API Gateway Integration

The service expects the API Gateway to provide user context via HTTP headers:

| Header | Description |
|--------|-------------|
| `X-User-Id` | User's UUID |
| `X-User-Email` | User's email (optional) |
| `X-User-Type` | User role (JOB_SEEKER, EMPLOYER) |

### 6.2 Authorization Rules

**Job Seeker:**
- Can submit/withdraw applications
- Can view their own applications
- Can save/unsave jobs
- Can view recommendations
- Can provide recommendation feedback

**Employer:**
- Can create/update job postings
- Can view applications for their jobs
- Can update application status
- Can download applicant resumes

---

## 7. Configuration

### 7.1 Application Properties

```yaml
server:
  port: 8083

spring:
  application:
    name: application-service

  datasource:
    url: jdbc:mysql://localhost:3306/jobseeker
    username: root
    password: password

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true

  kafka:
    bootstrap-servers: localhost:9092

user-profile-service:
  url: http://localhost:8081

auth-service:
  url: http://localhost:8083
```

### 7.2 Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | Service port | 8083 |
| `SPRING_DATASOURCE_URL` | Database URL | jdbc:mysql://localhost:3306/jobseeker |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | Kafka servers | localhost:9092 |
| `USER_PROFILE_SERVICE_URL` | Profile service URL | http://localhost:8081 |

---

## 8. Error Handling

### 8.1 Custom Exceptions

| Exception | HTTP Status | Description |
|-----------|-------------|-------------|
| `AlreadyAppliedException` | 400 | User already applied |
| `ApplicationNotFoundException` | 404 | Application not found |
| `CannotWithdrawException` | 400 | Cannot withdraw in current state |
| `JobNotFoundException` | 404 | Job not found |
| `JobAlreadySavedException` | 400 | Job already saved |
| `SavedJobNotFoundException` | 404 | Saved job not found |
| `UnauthorizedAccessException` | 403 | Access denied |

### 8.2 Global Exception Handler

The `GlobalExceptionHandler` provides centralized error handling and consistent error responses.

---

## 9. Data Flow Examples

### 9.1 Submit Application Flow

```
1. Job Seeker → POST /api/v1/applications
2. ApplicationController.extractUserId() → Get user ID from header
3. ApplicationService.submitApplication()
   ├── Validate job exists and is published
   ├── Check for duplicate application
   ├── Create Application entity
   ├── Save to database
   └── Publish Kafka event (APPLICATION_SUBMITTED)
4. Return ApplicationDetailsResponse
```

### 9.2 Get Recommendations Flow

```
1. Job Seeker → GET /api/v1/jobs/recommendations
2. RecommendationService.getRecommendations()
   ├── Check cache for existing recommendations
   ├── If expired or empty:
   │   ├── Fetch user profile from Auth Service
   │   ├── Get all active jobs
   │   ├── Calculate match scores
   │   └── Cache recommendations
   └── Return paginated recommendations
```

### 9.3 Profile Change Triggered Update Flow

```
1. User updates profile in Auth Service
2. Auth Service → Publish to profile-changes topic
3. ProfileEventConsumer.consumeProfileChangeEvent()
   ├── Identify event type (SKILL, EXPERIENCE, EDUCATION)
   ├── Fetch updated user profile
   ├── Get all active jobs
   └── Trigger recommendation recalculation
4. RecommendationService.recalculateAndCacheRecommendations()
   ├── Calculate new match scores
   ├── Clear old cache
   └── Save new recommendations
```

---

## 10. Future Enhancements

1. **Caching Strategy**: Implement Redis for caching recommendations
2. **Search Functionality**: Add full-text search for jobs
3. **Notification Service**: Integrate with push notifications
4. **Analytics**: Add application tracking analytics
5. **Rate Limiting**: Implement API rate limiting
6. **Circuit Breaker**: Add resilience patterns for external services
