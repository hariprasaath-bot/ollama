

Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/chat/docs' -Headers @{ 'Content-Type' = 'application/json' } -Body '{
"model": "gemma3:4b",
"prompt": "From the business problem statement documents, extract the User Requirements as concise bullet points. Include: Core user needs and goals; Functional requirements; Non-functional requirements; Constraints and assumptions; Key stakeholders/actors and their responsibilities. Keep the answer concise and only based on the provided documents",
"options": {"temperature": 0.2}
}'


lRuYYsSBHcmGVjZEzOvJRvoxUdASWTvvyd

_CsDDwhGDghMUAfXnsAjxVyAfYsfKyQAlzM
