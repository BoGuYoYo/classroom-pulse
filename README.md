# Classroom Pulse

A classroom interaction system for university courses. Teachers and students use the same Android app, with role-specific workspaces for courses, attendance, classroom questions, photo assignments, and teaching analytics.

This public repository contains source code, generic deployment templates, and tests only. It does not include real deployment credentials, server addresses, demo accounts, screenshots, generated data, or build artifacts.

## Features

- **Accounts and authorization**: student registration by student number, JWT authentication, BCrypt password hashing, self-service password change, teacher-assisted student password reset, pre-provisioned teacher accounts, and strict teacher/student/course ownership checks.
- **Courses**: teachers create, rename, archive, soft-delete courses, rotate invite codes and manage rosters; students join by invite code and can leave a course.
- **Attendance**: server-generated six-digit codes, validity windows, course-member checks, duplicate protection, and present/absent lists for teachers.
- **Classroom questions**: single-choice, multiple-choice and fill-in-the-blank questions, per-question deadlines, editing, early close, soft delete, server-side auto-grading (multiple-choice requires the exact correct set), one submission per student, and teacher analytics.
- **Photo assignments**: camera/gallery upload, JPEG/PNG validation, 10 MB limit, replacement before deadline, private image access, teacher review, scoring and written feedback.
- **Analytics**: teacher course analytics plus a per-student summary of attendance, answered questions and assignment grades. Unanswered questions are excluded from the question average.
- **Live updates**: the course workspace automatically refreshes every five seconds, so roster, attendance, answers, submissions and grades stay current.
- **Data safety**: database/file consistency on assignment replacement and serialized per-student concurrent replacements.

## Architecture

```text
Android (Kotlin, Jetpack Compose, ViewModel, Retrofit, DataStore, Coil)
        │ HTTPS + JSON / multipart
        ▼
Reverse proxy (Nginx or Caddy)
        │
        ▼
Spring Boot (Kotlin, Security, JPA, Flyway)
        ├── MySQL 8.4+
        └── local uploads directory (UUID filenames)
```

The repository follows a monorepo layout:

```text
backend/       Spring Boot API, Flyway migration, tests
android/       Android app, Compose UI, unit tests
deploy/        Generic Docker Compose and Nginx templates
docs/          Requirements, database, API, deployment and design notes
scripts/       Local environment, build, deployment and backup helpers
```

## Quick Start

### 1. Start MySQL and the backend locally

```powershell
.\scripts\new-local-env.ps1
docker compose --env-file .env.local -f docker-compose.local.yml up -d --build
Invoke-RestMethod http://127.0.0.1:8080/actuator/health
```

The generated `.env.local` contains random database/JWT secrets and a one-time bootstrap teacher credential. The script prints the generated teacher username/password to the local terminal; it is ignored by Git.

No demo course or student accounts are bundled.

### 2. Build the Android app

```powershell
cd android
$env:JAVA_HOME = "<JDK 17+ path>"
.\gradlew.bat assembleDebug -PCLASSROOM_API_BASE_URL=http://10.0.2.2:8080/
```

The default debug base URL targets an Android emulator. Debug builds expose a server-settings control; release builds embed the URL at build time and hide the server address. Build a release with:

```powershell
.\gradlew.bat assembleDebug -PCLASSROOM_API_BASE_URL=https://<your-domain>/
```

A release build needs a signing keystore and an HTTPS base URL; see `docs/05-部署与复现.md`.

### 3. Run tests

```powershell
cd backend
.\gradlew.bat test --rerun-tasks --no-daemon

cd ..\android
.\gradlew.bat testDebugUnitTest --rerun-tasks --no-daemon
```

Expected suites:

- `ClassroomApiFlowTest`: authentication, permissions, courses, attendance, questions, assignments, stats.
- `AssignmentFileSafetyTest`: transaction rollback and concurrent assignment replacement.
- `AuthUiStateTest`: authentication loading state convergence.

## Deployment

Use the generic templates in `deploy/`:

```bash
cd deploy
cp .env.example .env
# Fill DOMAIN, database secrets, JWT_SECRET and one-time BOOTSTRAP_TEACHER_* values.
cd ..
./scripts/deploy.sh
```

`deploy/docker-compose.yml` starts MySQL, the backend, and Nginx with HTTPS. TLS certificates are mounted from the host's `/etc/letsencrypt`; adjust the Nginx template if your environment uses another certificate manager. The backend only exposes images after JWT and course ownership checks; uploaded files are never served directly by the reverse proxy.

Detailed instructions: [deployment and reproduction](docs/05-部署与复现.md).

## Admin Console

The backend serves two separate pages: `/admin/login` for administrator sign-in and `/admin/` for the management console. Visiting the console without a session redirects to the login page; visiting the login page with a valid session redirects to the console. The console uses an independent server-side admin session, HttpOnly cookie, CSRF protection, login throttling, and secure logout. Teacher and student JWTs cannot access `/api/admin/**`.

Set `BOOTSTRAP_ADMIN_USERNAME` and `BOOTSTRAP_ADMIN_PASSWORD` for the first admin start, then remove the password from the environment. The console supports account and course lifecycle management, invitation resets, member removal, soft and permanent course deletion with impact previews, and read-only teaching data. It never edits questions, deadlines, scores, or feedback on behalf of teachers.

The reverse proxy must forward `/admin` and `/api/admin` to the backend. The bundled Nginx template does this; a Caddy equivalent needs `handle /admin*` and the existing `/api/*` proxy.

## Documentation

- [Requirements and design](docs/01-需求与设计.md)
- [Database design](docs/02-数据库设计.md)
- [API reference](docs/03-接口文档.md)
- [Deployment and reproduction](docs/05-部署与复现.md)
- [Implementation notes](docs/07-开发方案与实现说明.md)

## Security

- Keep `.env`, keystores, tokens, database passwords, and uploaded student images out of Git.
- Use HTTPS in production. Release Android builds reject cleartext traffic.
- Do not expose MySQL or the backend directly to the internet.
- Password changes and teacher-assisted resets invalidate all older JWTs through a per-user auth version; the current device receives a new token after a self-service password change.
- Use a strong, unique bootstrap teacher password and remove `BOOTSTRAP_TEACHER_PASSWORD` from the environment after the first successful login.
- Debug builds allow a runtime server URL for development. Release builds hide the server address and use the build-time HTTPS URL.

## Scope

The first version does not include push notifications, real-time chat, location attendance, face recognition, assignment grading, or multi-image submissions.

## License

No license has been declared for this repository.


