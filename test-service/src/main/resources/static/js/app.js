async function readJson(url, options = {}) {
  const res = await fetch(url, options);
  return await res.json();
}

function authHeaders(extraHeaders = {}) {
  const token = localStorage.getItem('msa_access_token');
  return token ? { Authorization: `Bearer ${token}`, ...extraHeaders } : extraHeaders;
}

function writeResult(id, value) {
  document.getElementById(id).textContent = JSON.stringify(value, null, 2);
}

function setHrEmployee(employeeNo, employeeName, status) {
  document.getElementById('member-no').value = employeeNo;
  document.getElementById('member-name').value = employeeName;
  document.getElementById('member-status').value = status;
}

document.getElementById('btn-gateway-members').addEventListener('click', async () => {
  writeResult('gateway-result', await readJson('/api/gateway/members'));
});

document.getElementById('btn-gateway-auth').addEventListener('click', async () => {
  writeResult('gateway-result', await readJson('/api/gateway/auth-login'));
});

document.getElementById('btn-gateway-configs').addEventListener('click', async () => {
  writeResult('gateway-result', await readJson('/api/gateway/configs'));
});

document.getElementById('btn-hr-active-1').addEventListener('click', () => {
  setHrEmployee('EMPLOYEE-10001', '홍길동', 'ACTIVE');
});

document.getElementById('btn-hr-active-2').addEventListener('click', () => {
  setHrEmployee('EMPLOYEE-10003', '이영희', 'ACTIVE');
});

document.getElementById('btn-hr-inactive-1').addEventListener('click', () => {
  setHrEmployee('EMPLOYEE-10002', '김철수', 'ACTIVE');
});

document.getElementById('btn-hr-inactive-2').addEventListener('click', () => {
  setHrEmployee('EMPLOYEE-10005', '최지훈', 'ACTIVE');
});

document.getElementById('btn-hr-select').addEventListener('click', async () => {
  const employeeNo = document.getElementById('member-no').value;
  writeResult('hr-result', await readJson(`/api/hr/employees/${encodeURIComponent(employeeNo)}`, { headers: authHeaders() }));
});

document.getElementById('btn-member-select-all').addEventListener('click', async () => {
  writeResult('member-result', await readJson('/api/member/members', { headers: authHeaders() }));
});

document.getElementById('btn-member-select-one').addEventListener('click', async () => {
  const memberNo = document.getElementById('member-no').value;
  writeResult('member-result', await readJson(`/api/member/members/${encodeURIComponent(memberNo)}`, { headers: authHeaders() }));
});

document.getElementById('btn-member-insert').addEventListener('click', async () => {
  const body = {
    memberNo: document.getElementById('member-no').value,
    memberName: document.getElementById('member-name').value,
    status: document.getElementById('member-status').value
  };
  writeResult('member-result', await readJson('/api/member/members', {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body)
  }));
});

document.getElementById('btn-member-signup').addEventListener('click', async () => {
  const body = {
    memberNo: document.getElementById('member-no').value,
    memberName: document.getElementById('member-name').value,
    status: document.getElementById('member-status').value
  };
  writeResult('member-result', await readJson('/api/member/members/signup', {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body)
  }));
});

document.getElementById('btn-member-processes').addEventListener('click', async () => {
  writeResult('member-result', await readJson('/api/member/members/processes', { headers: authHeaders() }));
});

document.getElementById('btn-member-timeline').addEventListener('click', async () => {
  const processId = document.getElementById('process-id').value || document.getElementById('member-no').value;
  writeResult('member-result', await readJson(`/api/member/members/processes/${encodeURIComponent(processId)}/timeline`, { headers: authHeaders() }));
});

document.getElementById('btn-auth-signup').addEventListener('click', async () => {
  const body = {
    userId: document.getElementById('auth-user-id').value,
    userName: document.getElementById('auth-user-name').value,
    password: document.getElementById('auth-password').value
  };
  const result = await readJson('/api/auth/signup', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  if (result.accessToken) {
    localStorage.setItem('msa_access_token', result.accessToken);
  }
  writeResult('auth-result', result);
  writeResult('token-result', { accessToken: result.accessToken, refreshToken: result.refreshToken });
});

document.getElementById('btn-auth-login').addEventListener('click', async () => {
  const body = {
    authType: document.getElementById('auth-type').value,
    userId: document.getElementById('auth-user-id').value,
    password: document.getElementById('auth-password').value
  };
  const result = await readJson('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body)
  });
  if (result.accessToken) {
    localStorage.setItem('msa_access_token', result.accessToken);
  }
  writeResult('auth-result', result);
  writeResult('token-result', { accessToken: result.accessToken, refreshToken: result.refreshToken });
});

document.getElementById('btn-config-select-all').addEventListener('click', async () => {
  writeResult('config-result', await readJson('/api/config/configs', { headers: authHeaders() }));
});

document.getElementById('btn-config-insert').addEventListener('click', async () => {
  const body = {
    configKey: document.getElementById('config-key').value,
    configValue: document.getElementById('config-value').value
  };
  writeResult('config-result', await readJson('/api/config/configs', {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body)
  }));
});

document.getElementById('btn-trace-all').addEventListener('click', async () => {
  writeResult('trace-result', await readJson('/api/trace/trace-events', { headers: authHeaders() }));
});

document.getElementById('btn-trace-by-trace-id').addEventListener('click', async () => {
  const traceId = document.getElementById('trace-id').value;
  writeResult('trace-result', await readJson(`/api/trace/trace-events/trace-id/${encodeURIComponent(traceId)}`, { headers: authHeaders() }));
});

document.getElementById('btn-trace-by-process-id').addEventListener('click', async () => {
  const processId = document.getElementById('process-id').value;
  writeResult('trace-result', await readJson(`/api/trace/trace-events/process-id/${encodeURIComponent(processId)}`, { headers: authHeaders() }));
});

document.getElementById('btn-trace-by-status').addEventListener('click', async () => {
  const status = document.getElementById('trace-status').value;
  writeResult('trace-result', await readJson(`/api/trace/trace-events/status/${encodeURIComponent(status)}`, { headers: authHeaders() }));
});

document.getElementById('btn-trace-by-both').addEventListener('click', async () => {
  const processId = document.getElementById('process-id').value;
  const traceId = document.getElementById('trace-id').value;
  writeResult('trace-result', await readJson(`/api/trace/trace-events/process-id/${encodeURIComponent(processId)}/trace-id/${encodeURIComponent(traceId)}`, { headers: authHeaders() }));
});
