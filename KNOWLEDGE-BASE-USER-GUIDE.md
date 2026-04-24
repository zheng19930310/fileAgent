# Knowledge Base Quick Start Guide

## Overview

The FileAgent Knowledge Base allows you to upload documents and associate them with chat sessions. The system automatically persists this data to disk, so your knowledge base survives application restarts.

## Supported File Formats

- **Text Files**: `.txt`
- **Word Documents**: `.doc`, `.docx`
- **PDF Files**: `.pdf`
- **PowerPoint**: `.ppt`, `.pptx`
- **Excel Spreadsheets**: `.xls`, `.xlsx`

## API Endpoints

### Upload File to Knowledge Base

**Endpoint:** `POST /api/kb/upload`

**Parameters:**
- `sessionId` (query param): Unique session identifier
- `file` (form-data): The file to upload

**Example (curl):**
```bash
curl -X POST "http://localhost:8080/api/kb/upload?sessionId=my-session" \
  -F "file=@document.pdf"
```

**Response:**
```json
{
  "success": true,
  "fileName": "document.pdf",
  "chunks": 15,
  "contentPreview": "First 200 characters of the document..."
}
```

### Search Knowledge Base

**Endpoint:** `GET /api/kb/search`

**Parameters:**
- `sessionId` (query param): Session to search in
- `query` (query param): Search keywords

**Example:**
```bash
curl "http://localhost:8080/api/kb/search?sessionId=my-session&query=machine+learning"
```

**Response:**
```json
[
  "Chunk 1 containing machine learning concepts...",
  "Chunk 2 with related information...",
  ...
]
```

### Get Uploaded Files

**Endpoint:** `GET /api/kb/files`

**Parameters:**
- `sessionId` (query param): Session ID

**Example:**
```bash
curl "http://localhost:8080/api/kb/files?sessionId=my-session"
```

**Response:**
```json
[
  {
    "name": "document.pdf",
    "size": "1234567",
    "chunks": "15",
    "uploadTime": "2026-04-20 16:30:00"
  }
]
```

### Clear Session Knowledge Base

**Endpoint:** `DELETE /api/kb/session/{sessionId}`

**Example:**
```bash
curl -X DELETE "http://localhost:8080/api/kb/session/my-session"
```

### Clear All Knowledge Bases

**Endpoint:** `DELETE /api/kb/all`

**Example:**
```bash
curl -X DELETE "http://localhost:8080/api/kb/all"
```

## How It Works

### 1. File Upload Process

```
User uploads file
    ↓
File is saved to D:/chat/kb/
    ↓
Content is extracted based on file type
    ↓
Text is split into chunks (500 chars each)
    ↓
Chunks are stored in memory
    ↓
Chunks are persisted to D:/chat/kb/.data/{sessionId}.kb
    ↓
File metadata is persisted to D:/chat/kb/.meta/{sessionId}.json
```

### 2. Application Startup

```
Application starts
    ↓
@PostConstruct init() runs
    ↓
Directories created if needed
    ↓
All .kb files loaded from D:/chat/kb/.data/
    ↓
All .json files loaded from D:/chat/kb/.meta/
    ↓
Knowledge bases restored in memory
    ↓
Ready to serve requests
```

### 3. Search Process

```
User searches with query
    ↓
Query is split into keywords
    ↓
Each chunk is scored by keyword matches
    ↓
Top 5 most relevant chunks returned
    ↓
Results sorted by relevance score
```

## Storage Structure

```
D:/chat/kb/
├── uploaded_files/          # Original uploaded files
│   ├── 20260420_163000_document.pdf
│   └── ...
├── .data/                   # KB chunk storage
│   ├── session-1.kb         # Chunks for session 1
│   ├── session-2.kb         # Chunks for session 2
│   └── ...
└── .meta/                   # File metadata
    ├── session-1.json       # File list for session 1
    ├── session-2.json       # File list for session 2
    └── ...
```

## Best Practices

### Session Management

1. **Use meaningful session IDs**: Instead of random UUIDs, use user IDs or project names
   ```
   Good: "user-123-project-alpha"
   Bad: "a3f2b1c4-5d6e-7f8g-9h0i-j1k2l3m4n5o6"
   ```

2. **One session per conversation**: Create a new session for each distinct conversation topic

3. **Clear old sessions**: Regularly clean up sessions that are no longer needed

### File Upload

1. **File size limits**: Keep individual files under 10MB for best performance
2. **Chunk size**: System uses 500-character chunks with 100-char overlap
3. **Multiple files**: You can upload multiple files to the same session

### Search Optimization

1. **Use specific keywords**: More specific queries return better results
2. **Avoid common words**: Words like "the", "is", "at" are ignored
3. **Multi-word queries**: Use spaces to separate important terms

## Troubleshooting

### Issue: Files not persisting

**Check:**
1. Directory permissions on `D:/chat/kb/`
2. Disk space availability
3. Application logs for I/O errors

**Solution:**
```bash
# Check directory exists and is writable
ls -la D:/chat/kb/

# Check application logs
tail -f logs/application.log | grep "持久化"
```

### Issue: Data not loading on startup

**Check:**
1. Persistence files exist in `.data/` and `.meta/` directories
2. File format is correct (no corruption)
3. Application has read permissions

**Solution:**
```bash
# Verify persistence files
ls -la D:/chat/kb/.data/
ls -la D:/chat/kb/.meta/

# Check file content
cat D:/chat/kb/.data/session-1.kb
```

### Issue: Search returns no results

**Possible causes:**
1. No files uploaded to the session
2. Query keywords don't match any chunks
3. Session ID mismatch

**Solution:**
```bash
# Check if session has knowledge base
curl "http://localhost:8080/api/kb/files?sessionId=my-session"

# Try broader search terms
curl "http://localhost:8080/api/kb/search?sessionId=my-session&query=broad+term"
```

## Performance Tips

1. **Chunk Size**: Current 500-char chunks balance search precision and performance
2. **Memory Usage**: Each session's KB is loaded into memory - monitor for large deployments
3. **Search Speed**: Typically <10ms for sessions with <100 chunks
4. **Upload Speed**: Depends on file size and parsing complexity

## Monitoring

Key metrics to monitor:

- Number of active sessions
- Total chunks across all sessions
- Average chunks per session
- Upload success rate
- Search response time

Check via logs:
```
INFO  - 上传知识库文件: sessionId=xxx, fileName=yyy, chunks=15
INFO  - 加载知识库: sessionId=xxx, chunks=150
INFO  - 共加载 10 个会话的知识库
```

## Examples

### Example 1: Project Documentation

```bash
# Create session for project
SESSION="project-alpha-docs"

# Upload requirements document
curl -X POST "http://localhost:8080/api/kb/upload?sessionId=$SESSION" \
  -F "file=@requirements.docx"

# Upload technical spec
curl -X POST "http://localhost:8080/api/kb/upload?sessionId=$SESSION" \
  -F "file=@tech-spec.pdf"

# Search for specific information
curl "http://localhost:8080/api/kb/search?sessionId=$SESSION&query=authentication+security"
```

### Example 2: Meeting Notes

```bash
# Session for meeting series
SESSION="team-meetings-2026"

# Upload weekly notes
curl -X POST "http://localhost:8080/api/kb/upload?sessionId=$SESSION" \
  -F "file=@week1-notes.txt"

curl -X POST "http://localhost:8080/api/kb/upload?sessionId=$SESSION" \
  -F "file=@week2-notes.txt"

# Search for action items
curl "http://localhost:8080/api/kb/search?sessionId=$SESSION&query=action+item+deadline"
```

### Example 3: Research Papers

```bash
# Session for research topic
SESSION="ml-research"

# Upload multiple papers
for paper in papers/*.pdf; do
  curl -X POST "http://localhost:8080/api/kb/upload?sessionId=$SESSION" \
    -F "file=@$paper"
done

# Find relevant sections
curl "http://localhost:8080/api/kb/search?sessionId=$SESSION&query=neural+network+architecture"
```

## FAQ

**Q: How long does data persist?**
A: Indefinitely, until you explicitly clear the session or all data.

**Q: Can I share knowledge bases between sessions?**
A: Not currently. Each session has its own isolated KB.

**Q: What happens if I upload the same file twice?**
A: It will be processed and added again, creating duplicate chunks.

**Q: Is there a limit on file size?**
A: No hard limit, but files >10MB may cause performance issues.

**Q: Can I export my knowledge base?**
A: The raw data is in `D:/chat/kb/.data/` in text format.

**Q: Does it support concurrent uploads?**
A: Yes, the system is thread-safe for concurrent operations.

## Support

For issues or questions:
1. Check application logs in `logs/` directory
2. Verify file permissions on `D:/chat/kb/`
3. Review this guide for common scenarios
4. Check test cases for usage examples

---

**Version:** 1.0.0
**Last Updated:** 2026-04-20
