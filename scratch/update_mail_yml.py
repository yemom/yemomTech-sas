with open('backend/src/main/resources/application.yml', 'r', encoding='utf-8') as f:
    content = f.read()

target = "  jackson:\n    serialization:\n      write-dates-as-timestamps: false"
replacement = """  jackson:
    serialization:
      write-dates-as-timestamps: false

  mail:
    host: ${SPRING_MAIL_HOST:}
    port: ${SPRING_MAIL_PORT:587}
    username: ${SPRING_MAIL_USERNAME:}
    password: ${SPRING_MAIL_PASSWORD:}
    properties:
      mail:
        smtp:
          auth: ${SPRING_MAIL_SMTP_AUTH:true}
          starttls:
            enable: ${SPRING_MAIL_SMTP_STARTTLS_ENABLE:true}"""

content_normalized = content.replace('\r\n', '\n')
if target in content_normalized:
    new_content = content_normalized.replace(target, replacement)
    with open('backend/src/main/resources/application.yml', 'w', encoding='utf-8', newline='\n') as f:
        f.write(new_content)
    print("SUCCESS")
else:
    print("NOT FOUND")
