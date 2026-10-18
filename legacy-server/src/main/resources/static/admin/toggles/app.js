'use strict';

const list = document.querySelector('#toggles');
const template = document.querySelector('#toggle-template');
const refresh = document.querySelector('#refresh');
const globalMessage = document.querySelector('#global-message');
const connection = document.querySelector('#connection');
const states = new Map();

function validRatio(raw) {
  if (String(raw).trim() === '') return false;
  const value = Number(raw);
  return Number.isInteger(value) && value >= 0 && value <= 100;
}

async function api(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(url, { ...options, signal: controller.signal, cache: 'no-store' });
    const body = await response.json();
    if (!response.ok) throw new Error(body.message || `요청에 실패했습니다 (${response.status}).`);
    return body;
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('응답을 확인하지 못했습니다. 새로고침으로 저장 상태를 확인해 주세요.');
    throw error;
  } finally {
    clearTimeout(timeout);
  }
}

function renderToggle(toggle) {
  const card = template.content.firstElementChild.cloneNode(true);
  const slider = card.querySelector('.ratio-slider');
  const number = card.querySelector('.ratio-number');
  const save = card.querySelector('.save');
  const reset = card.querySelector('.reset');
  const message = card.querySelector('.card-message');
  const state = { saved: toggle.newPercent, draft: toggle.newPercent, saving: false };
  states.set(toggle.key, state);
  card.querySelector('h3').textContent = toggle.name;
  card.querySelector('.toggle-key').textContent = toggle.key;
  card.querySelector('.card-description').textContent = toggle.connected
    ? '사용자 기준으로 입금·출금·조회 경로를 함께 관리합니다.'
    : '현재 업무 API에는 연결되지 않은 예비 토글입니다.';
  const usage = card.querySelector('.usage');
  usage.textContent = toggle.connected ? '라우팅 적용 중' : '예비 토글';
  usage.classList.add(toggle.connected ? 'active' : 'inactive');
  slider.setAttribute('aria-label', `${toggle.name} 신규 비율 슬라이더`);
  number.setAttribute('aria-label', `${toggle.name} 신규 비율`);

  function sync() {
    const valid = validRatio(state.draft);
    const dirty = !valid || Number(state.draft) !== state.saved;
    if (valid) {
      slider.value = state.draft;
      slider.style.setProperty('--ratio', `${state.draft}%`);
      card.querySelector('.ratio-display strong').textContent = state.draft;
      card.querySelector('.legacy-percent').textContent = `${100 - Number(state.draft)}%`;
      card.querySelector('.new-percent').textContent = `${state.draft}%`;
    }
    number.value = state.draft;
    number.setAttribute('aria-invalid', String(!valid));
    save.disabled = !valid || !dirty || state.saving;
    reset.disabled = !dirty || state.saving;
    slider.disabled = state.saving;
    number.disabled = state.saving;
    save.textContent = state.saving ? '저장 중…' : '변경 저장 ↗';
    message.className = `card-message${!valid ? ' error' : dirty ? ' dirty' : ''}`;
    message.textContent = !valid ? '0부터 100까지 정수를 입력해 주세요.'
      : dirty ? `저장 전 · 신규 ${state.saved}% → ${state.draft}%` : '현재 설정과 같습니다.';
  }

  slider.addEventListener('input', () => { state.draft = Number(slider.value); sync(); });
  number.addEventListener('input', () => { state.draft = number.value; sync(); });
  reset.addEventListener('click', () => { state.draft = state.saved; sync(); });
  card.querySelector('form').addEventListener('submit', async event => {
    event.preventDefault();
    if (!validRatio(state.draft) || state.saving || Number(state.draft) === state.saved) return;
    state.saving = true;
    sync();
    try {
      const saved = await api(`/admin/api/toggles/${encodeURIComponent(toggle.key)}`, {
        method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ newPercent: Number(state.draft) }),
      });
      state.saved = saved.newPercent;
      state.draft = saved.newPercent;
      state.saving = false;
      sync();
      message.className = 'card-message success';
      message.textContent = '저장 완료 · 신규 사용자부터 적용됩니다.';
    } catch (error) {
      state.saving = false;
      sync();
      message.className = 'card-message error';
      message.textContent = error.message || '저장하지 못했습니다. 다시 시도해 주세요.';
    }
  });
  sync();
  return card;
}

async function load() {
  if ([...states.values()].some(state => state.saving || !validRatio(state.draft) || Number(state.draft) !== state.saved)) {
    globalMessage.textContent = '저장하지 않은 변경이 있습니다. 저장하거나 되돌린 뒤 새로고침해 주세요.';
    return;
  }
  refresh.disabled = true;
  globalMessage.textContent = '';
  document.querySelector('#loading').hidden = false;
  try {
    const toggles = await api('/admin/api/toggles');
    states.clear();
    list.replaceChildren(...toggles.map(renderToggle));
    document.querySelector('#toggle-count').textContent = toggles.length;
    document.querySelector('#list-count').textContent = toggles.length;
    connection.textContent = '연결됨';
    connection.className = 'badge online';
    document.querySelector('#last-refresh').textContent = `${new Date().toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })} 기준`;
  } catch (error) {
    globalMessage.textContent = error.message || '설정을 불러오지 못했습니다. Redis 연결을 확인해 주세요.';
    connection.textContent = '연결 확인 필요';
    connection.className = 'badge offline';
    document.querySelector('#last-refresh').textContent = '마지막 조회에 실패했습니다';
  } finally {
    document.querySelector('#loading').hidden = true;
    refresh.disabled = false;
  }
}

refresh.addEventListener('click', load);
load();
