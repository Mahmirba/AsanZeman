# Generates erd_business.dot and erd_support.dot (Graphviz, HTML-table nodes, crow's-foot edges)
import sys
HDR = '''digraph {{
  graph [rankdir=TB, splines=spline, nodesep=0.45, ranksep=0.7, bgcolor=white, fontname="Helvetica", pad=0.2];
  node  [shape=plain, fontname="Helvetica", fontsize=11];
  edge  [fontname="Helvetica", fontsize=10, color="#64748B", fontcolor="#7C2D12", penwidth=1.2, arrowsize=0.9];
{body}
}}
'''
def entity(name, cols, schema):
    rows = ''.join(
        f'<TR><TD ALIGN="LEFT" PORT="{c[0]}"><FONT COLOR="#1E3A8A">{c[0]}</FONT></TD>'
        f'<TD ALIGN="LEFT"><FONT COLOR="#475569">{c[1]}</FONT></TD>' +
        (f'<TD ALIGN="LEFT"><FONT COLOR="#9A3412"><B>{c[2]}</B></FONT></TD></TR>' if len(c)>2 else '<TD></TD></TR>') for c in cols)
    return (f'  {name} [label=<<TABLE BORDER="0" CELLBORDER="1" CELLSPACING="0" CELLPADDING="4" COLOR="#93B4E8">'
            f'<TR><TD COLSPAN="3" BGCOLOR="#1E3A8A"><FONT COLOR="white"><B>{name}</B></FONT>  <FONT COLOR="#BFDBFE" POINT-SIZE="9">{schema}</FONT></TD></TR>'
            f'{rows}</TABLE>>];')
# edge: parent -> child, one-to-many (crow at child), optional parent (odot)
def rel(p, c, label, kind='1n'):
    head = {'1n':'crow', '11':'teeodot', '1n_opt':'crowodot'}[kind]
    return f'  {p} -> {c} [label="{label}", arrowhead={head}, arrowtail=tee, dir=both];'

B = [
 entity('CUSTOMER',[('id','RAW(16)','PK'),('cif','VARCHAR2'),('national_code','VARCHAR2(10)'),('first_name','VARCHAR2'),('last_name','VARCHAR2'),('father_name','VARCHAR2'),('birth_date','DATE'),('mobile','VARCHAR2(11)'),('ssn','VARCHAR2'),('id_series / id_serial','VARCHAR2'),('personality_type','VARCHAR2'),('gender','VARCHAR2')],'AZS_BUSINESS'),
 entity('AGENCY',[('id','RAW(16)','PK'),('cif','VARCHAR2','UK'),('code','VARCHAR2(4)'),('name','VARCHAR2'),('active','NUMBER(1)')],'AZS_BUSINESS'),
 entity('BRANCH',[('branch_code','VARCHAR2(10)','PK'),('name','VARCHAR2'),('city','VARCHAR2'),('address / tel / fax / zip','VARCHAR2'),('parent_branch','VARCHAR2(10)','FK'),('active','NUMBER(1)'),('last_synced_at','TIMESTAMP')],'AZS_BUSINESS'),
 entity('REJECTION_REASON',[('id','RAW(16)','PK'),('kind','ISSUANCE | REVOCATION'),('text','VARCHAR2'),('active','NUMBER(1)')],'AZS_BUSINESS'),
 entity('APP_USER',[('id','RAW(16)','PK'),('account_name','VARCHAR2','UK'),('prsn_code','VARCHAR2'),('branch_code','VARCHAR2(10)'),('position / job','VARCHAR2'),('role','BRANCH | ADMIN')],'AZS_AUTH'),
 entity('REQUEST',[('id','RAW(16)','PK'),('tracking_code','VARCHAR2(20)','UK'),('request_type','ISSUANCE | REVOCATION'),('parent_request_id','RAW(16)','FK'),('customer_id','RAW(16)','FK'),('agency_id','RAW(16)','FK'),('branch_code','VARCHAR2(10)','FK'),('amount','NUMBER(18)'),('expiry_date','DATE'),('status','VARCHAR2(32)'),('guarantee_number','VARCHAR2(30)'),('rejection_reason_id','RAW(16)','FK'),('rejection_comment','VARCHAR2(500)'),('locked_by / locked_by_name','RAW(16) / VARCHAR2','FK'),('locked_at / last_activity_at','TIMESTAMP'),('version','NUMBER(10)'),('registered_at … revoked_at','TIMESTAMP')],'AZS_BUSINESS'),
 entity('COMPANION',[('id','RAW(16)','PK'),('request_id','RAW(16)','FK'),('national_code','VARCHAR2(10)'),('first_name / last_name','VARCHAR2'),('relation','VARCHAR2')],'AZS_BUSINESS'),
 entity('COLLATERAL',[('id','RAW(16)','PK'),('request_id','RAW(16)','FK'),('kind','CASH | PLEDGE'),('deposit_number','VARCHAR2'),('deposit_class','CHEAP | EXPENSIVE'),('share_percent','NUMBER(3)'),('factor','NUMBER(3,2)'),('allocated_amount','NUMBER(18)')],'AZS_BUSINESS'),
 entity('INQUIRY',[('id','RAW(16)','PK'),('request_id','RAW(16)','FK'),('run_id','RAW(16)'),('inquiry_type','VARCHAR2'),('attempt_no','NUMBER(2)'),('result','PASSED | NEGATIVE | FAILED | SKIPPED'),('failure_kind','VARCHAR2'),('response_code / duration_ms','VARCHAR2 / NUMBER'),('provider_trace_id','VARCHAR2'),('raw_response','CLOB'),('executed_at','TIMESTAMP')],'AZS_INQUIRY'),
 '  {rank=same; CUSTOMER; AGENCY; BRANCH; REJECTION_REASON; APP_USER;}',
 '  {rank=same; COMPANION; COLLATERAL; INQUIRY;}',
 rel('CUSTOMER','REQUEST','1 : N'), rel('AGENCY','REQUEST','1 : N'), rel('BRANCH','REQUEST','1 : N  (original branch)'),
 rel('REJECTION_REASON','REQUEST','0..1 : N','1n_opt'), rel('APP_USER','REQUEST','locked_by  0..1 : N','1n_opt'),
 '  BRANCH -> BRANCH [label="parent_branch\\n(self or replacement)", arrowhead=teeodot, arrowtail=tee, dir=both];',
 '  REQUEST -> REQUEST [label="parent_request_id\\n(revocation → issuance)", arrowhead=teeodot, arrowtail=tee, dir=both];',
 rel('REQUEST','COMPANION','1 : 0..5'), rel('REQUEST','COLLATERAL','1 : N'), rel('REQUEST','INQUIRY','1 : N  (per attempt)'),
]
S = [
 entity('CUSTOMER',[('id','RAW(16)','PK'),('national_code','VARCHAR2(10)'),('mobile','VARCHAR2(11)')],'AZS_BUSINESS'),
 entity('REQUEST',[('id','RAW(16)','PK'),('tracking_code','VARCHAR2(20)','UK'),('status','VARCHAR2(32)')],'AZS_BUSINESS'),
 entity('OTP_CHALLENGE',[('id','RAW(16)','PK'),('customer_id','RAW(16)','FK'),('otp_id','VARCHAR2  (Notifier sId)'),('sms_id','VARCHAR2'),('status','SENT | VERIFIED | EXPIRED'),('wrong_attempts','NUMBER(2)'),('locked_until','TIMESTAMP'),('sent_at','TIMESTAMP')],'AZS_AUTH'),
 entity('REFRESH_TOKEN',[('id','RAW(16)','PK'),('user_id','RAW(16)'),('user_type','BANK | AGENCY'),('token_hash','VARCHAR2(64)','UK'),('family_id','RAW(16)'),('expires_at','TIMESTAMP'),('used_at','TIMESTAMP')],'AZS_AUTH'),
 entity('JWT_BLACKLIST',[('jti','VARCHAR2','PK'),('expires_at','TIMESTAMP')],'AZS_AUTH'),
 entity('OUTBOX_MESSAGE',[('id','RAW(16)','PK'),('request_id','RAW(16)','FK'),('event','VARCHAR2  (SMS 1..8)'),('number','VARCHAR2(13)  0989…'),('message','VARCHAR2(1000)'),('status','PENDING | SENT | DEAD_LETTER'),('attempts','NUMBER(3)'),('next_attempt_at','TIMESTAMP'),('sms_id','VARCHAR2')],'AZS_OUTBOX'),
 entity('AUDIT_LOG',[('id','RAW(16)','PK'),('user_id / user_role','RAW(16) / VARCHAR2'),('entity_type','VARCHAR2'),('entity_id','RAW(16)'),('action','VARCHAR2'),('old_value / new_value','CLOB (JSON)'),('ip_address / user_agent','VARCHAR2'),('created_at','TIMESTAMP')],'AZS_AUDIT'),
 entity('TRACKING_COUNTER',[('branch_code','VARCHAR2(10)','PK'),('agency_code','VARCHAR2(4)','PK'),('day','VARCHAR2(6)  YYMMDD','PK'),('last_value','NUMBER(4)')],'AZS_BUSINESS'),
 entity('SCHEDULER_LOCK',[('job_name','VARCHAR2','PK'),('locked_by','VARCHAR2'),('locked_until','TIMESTAMP')],'AZS_BUSINESS'),
 '  {rank=same; CUSTOMER; REQUEST; AUDIT_LOG;}',
 '  {rank=same; OTP_CHALLENGE; OUTBOX_MESSAGE; REFRESH_TOKEN;}',
 '  {rank=same; JWT_BLACKLIST; TRACKING_COUNTER; SCHEDULER_LOCK;}',
 '  AUDIT_LOG -> REFRESH_TOKEN [style=invis]; REFRESH_TOKEN -> JWT_BLACKLIST [style=invis]; OTP_CHALLENGE -> TRACKING_COUNTER [style=invis]; OUTBOX_MESSAGE -> SCHEDULER_LOCK [style=invis];',
 rel('CUSTOMER','OTP_CHALLENGE','1 : N'), rel('REQUEST','OUTBOX_MESSAGE','1 : N'),
 '  AUDIT_LOG -> REQUEST [label="entity_id (logical, no FK)", style=dashed, arrowhead=none, arrowtail=none, dir=both, constraint=false];',
]
open('erd_business.dot','w').write(HDR.format(body='\n'.join(B)))
open('erd_support.dot','w').write(HDR.format(body='\n'.join(S)))
