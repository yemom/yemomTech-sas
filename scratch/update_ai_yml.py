with open('backend/src/main/resources/application.yml', 'r', encoding='utf-8') as f:
    content = f.read()

target = """  frontend:
    base-url: ${FRONTEND_URL:http://localhost:3000}"""

replacement = """  frontend:
    base-url: ${FRONTEND_URL:http://localhost:3000}
  ai:
    openai:
      api-key: ${OPENAI_API_KEY:}
      model: ${OPENAI_MODEL:gpt-4o-mini}
      base-url: ${OPENAI_BASE_URL:https://api.openai.com/v1}"""

content_normalized = content.replace('\r\n', '\n')
if target in content_normalized:
    new_content = content_normalized.replace(target, replacement)
    with open('backend/src/main/resources/application.yml', 'w', encoding='utf-8', newline='\n') as f:
        f.write(new_content)
    print("SUCCESS")
else:
    print("NOT FOUND")
