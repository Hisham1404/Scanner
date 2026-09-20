/* Findings log -> a written record and a complaint draft. */

const Report = {
  init() {
    this.render();
    $('#btnCopy').addEventListener('click', () => this.copy());
    $('#btnDownload').addEventListener('click', () => this.download());
    $('#btnClear').addEventListener('click', () => this.clear());
    ['place', 'who'].forEach((id) => {
      const el = document.getElementById(id);
      el.value = Store.get('ctx:' + id, '') || '';
      el.addEventListener('input', () => {
        Store.set('ctx:' + id, el.value);
        this.renderDraft();
      });
    });
  },

  render() {
    const list = Findings.all();
    const host = $('#findingsList');
    host.innerHTML = '';

    if (!list.length) {
      host.innerHTML = '<p class="empty">Nothing flagged yet. Run a sweep or a scan, and anything you mark suspicious shows up here.</p>';
      $('#draftWrap').hidden = true;
      return;
    }

    $('#draftWrap').hidden = false;

    list.slice().reverse().forEach((f) => {
      const div = document.createElement('div');
      div.className = 'findings-item';
      div.innerHTML =
        '<h4>' + this.esc(f.title) + '</h4>' +
        '<div class="meta">' + this.esc(f.zone) + ' · ' + fmtTime(f.at) + '</div>' +
        (f.note ? '<p class="small" style="margin:6px 0 0">' + this.esc(f.note) + '</p>' : '');

      const del = document.createElement('button');
      del.className = 'btn btn-sm';
      del.type = 'button';
      del.style.marginTop = '9px';
      del.textContent = 'Remove';
      del.addEventListener('click', () => {
        Findings.remove(f.id);
        this.render();
        markFindingsBadge();
      });
      div.appendChild(del);
      host.appendChild(div);
    });

    this.renderDraft();
  },

  esc(s) {
    return String(s === undefined || s === null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  },

  draftText() {
    const list = Findings.all();
    const place = (document.getElementById('place').value || '[address / name of the place]').trim();
    const who = (document.getElementById('who').value || '[your name]').trim();
    const now = new Date();

    const lines = [];
    lines.push('RECORD OF A HIDDEN-CAMERA SEARCH');
    lines.push('');
    lines.push('Prepared by : ' + who);
    lines.push('Location    : ' + place);
    lines.push('Prepared on : ' + now.toLocaleString());
    lines.push('');
    lines.push('I carried out a physical and optical search of the above premises.');
    lines.push('The following observations were recorded at the times shown.');
    lines.push('');
    lines.push('OBSERVATIONS');
    lines.push('------------');

    list.forEach((f, i) => {
      lines.push('');
      lines.push((i + 1) + '. ' + f.title);
      lines.push('   Area     : ' + f.zone);
      lines.push('   Recorded : ' + fmtTime(f.at));
      if (f.note) lines.push('   Detail   : ' + f.note);
    });

    lines.push('');
    lines.push('');
    lines.push('STATEMENT');
    lines.push('---------');
    lines.push('');
    lines.push('To the Station House Officer / Cyber Cell,');
    lines.push('');
    lines.push('I wish to report the suspected presence of a concealed recording device at');
    lines.push(place + '. The observations listed above were made by me on ' +
               now.toLocaleDateString() + '.');
    lines.push('');
    lines.push('I request that the premises be inspected and that any device found be seized');
    lines.push('and examined. I believe the matter attracts Section 77 of the Bharatiya Nyaya');
    lines.push('Sanhita, 2023 (voyeurism) and Section 66E of the Information Technology Act,');
    lines.push('2000 (violation of privacy).');
    lines.push('');
    lines.push('I have not moved, switched off or interfered with anything I suspected, so');
    lines.push('that the scene and any stored footage remain intact.');
    lines.push('');
    lines.push('Signature : ______________________');
    lines.push('Name      : ' + who);
    lines.push('Phone     : ______________________');
    lines.push('Date      : ' + now.toLocaleDateString());
    lines.push('');
    lines.push('---');
    lines.push('Observations recorded with Scanner. This is a personal record written by the');
    lines.push('person named above, not a forensic finding, and not legal advice.');

    return lines.join('\n');
  },

  renderDraft() {
    $('#draft').value = this.draftText();
  },

  async copy() {
    const text = this.draftText();
    try {
      await navigator.clipboard.writeText(text);
      $('#copyNote').textContent = 'Copied.';
    } catch (e) {
      const ta = $('#draft');
      ta.select();
      $('#copyNote').textContent = 'Select-all and copy manually.';
    }
    setTimeout(() => { $('#copyNote').textContent = ''; }, 2500);
  },

  download() {
    const blob = new Blob([this.draftText()], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'hidden-camera-report-' + new Date().toISOString().slice(0, 10) + '.txt';
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 4000);
  },

  clear() {
    if (!confirm('Delete every logged finding? This cannot be undone.')) return;
    Findings.clear();
    this.render();
    markFindingsBadge();
  }
};

document.addEventListener('DOMContentLoaded', () => {
  if ($('#findingsList')) Report.init();
});
