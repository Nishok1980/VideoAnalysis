'use strict';
const $ = id => document.getElementById(id);
const canvas = $('zoneCanvas'), ctx = canvas.getContext('2d');
let image = null, area = {x:0,y:0,width:1,height:1}, anchor = null;
let selectedVideo = null, selectedJob = null, timer = null;
let videoList = [], jobList = [], job = null, stopAt = null;
let ready = false, pollGeneration = 0;

function notice(message, error = false) {
  $('notice').textContent = message;
  $('notice').classList.toggle('error', error);
}
async function api(url, options = {}) {
  const response = await fetch(url, {
    ...options, headers: {'X-VideoAnalysis':'1', ...options.headers}
  });
  const text = await response.text();
  let body;
  try { body = text ? JSON.parse(text) : null; }
  catch { throw new Error(`Request failed (${response.status}).`); }
  if (!response.ok) throw new Error(body?.error || `Request failed (${response.status}).`);
  return body;
}
const json = (method, body) => ({method, headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});
function handle(fn) { return async event => {
  event?.preventDefault();
  try { await fn(event); } catch (error) { notice(error.message, true); }
}; }
function option(value, label) { const el = document.createElement('option'); el.value=value;el.textContent=label;return el; }
function syncButtons() {
  const active = jobList.some(j=>['QUEUED','RUNNING'].includes(j.state));
  $('analyseButton').disabled = !ready || !selectedVideo || active;
  $('deleteVideo').disabled = !selectedVideo || active;
}
async function refreshVideos() {
  videoList = await api('/api/videos');
  $('videos').replaceChildren(option('', 'Choose a recording'));
  videoList.forEach(v=>$('videos').append(option(v.id, `${v.filename} · ${Math.round(v.durationSeconds)} s`)));
  $('videos').value = selectedVideo || '';
  syncButtons();
}
async function refreshJobs() {
  jobList = await api('/api/jobs');
  $('jobs').replaceChildren(option('', 'Choose an analysis'));
  jobList.forEach(j=>$('jobs').append(option(j.id, `${j.rules.areaName} · ${j.state} · ${j.id.slice(0,8)}`)));
  $('jobs').value=selectedJob || '';
  syncButtons();
}
function draw() {
  ctx.clearRect(0,0,canvas.width,canvas.height);
  if(image) ctx.drawImage(image,0,0,canvas.width,canvas.height);
  ctx.fillStyle='rgba(20,160,140,.17)';
  ctx.strokeStyle='#19dcc0';ctx.lineWidth=3;
  const box=[area.x*canvas.width,area.y*canvas.height,area.width*canvas.width,area.height*canvas.height];
  ctx.fillRect(...box);ctx.strokeRect(...box);
  $('areaSummary').textContent=`Area: ${Math.round(area.width*100)}% wide × ${Math.round(area.height*100)}% high`;
  for(const [key,id] of Object.entries({x:'areaX',y:'areaY',width:'areaWidth',height:'areaHeight'})) $(id).value=area[key].toFixed(3);
}
async function chooseVideo(id) {
  selectedVideo=id || null;image=null;area={x:0,y:0,width:1,height:1};draw();syncButtons();
  if(!id) return;
  const loaded = new Image();
  await new Promise((resolve,reject)=>{
    loaded.onload=resolve;loaded.onerror=()=>reject(new Error('Could not load video thumbnail.'));
    loaded.src=`/api/videos/${id}/thumbnail.jpg`;
  });
  if(selectedVideo!==id) return;
  image=loaded;canvas.width=loaded.naturalWidth;canvas.height=loaded.naturalHeight;draw();
}
function point(event) {
  const bounds=canvas.getBoundingClientRect();
  return {x:Math.max(0,Math.min(1,(event.clientX-bounds.left)/bounds.width)),y:Math.max(0,Math.min(1,(event.clientY-bounds.top)/bounds.height))};
}
canvas.addEventListener('pointerdown',event=>{if(!image)return;anchor=point(event);canvas.setPointerCapture(event.pointerId);});
canvas.addEventListener('pointermove',event=>{
  if(!anchor)return;const end=point(event);
  area={x:Math.min(anchor.x,end.x),y:Math.min(anchor.y,end.y),width:Math.abs(end.x-anchor.x),height:Math.abs(end.y-anchor.y)};draw();
});
canvas.addEventListener('pointerup',()=>{anchor=null;if(area.width<.01||area.height<.01)area={x:0,y:0,width:1,height:1};draw();});
canvas.addEventListener('pointercancel',()=>{anchor=null;});
$('wholeFrame').onclick=()=>{area={x:0,y:0,width:1,height:1};draw();};
$('applyArea').onclick=handle(()=>{
  const next={x:+$('areaX').value,y:+$('areaY').value,width:+$('areaWidth').value,height:+$('areaHeight').value};
  if(!Object.values(next).every(Number.isFinite)||next.x<0||next.y<0||next.width<=0||next.height<=0||next.x+next.width>1||next.y+next.height>1)throw new Error('Rectangle must fit inside the image.');
  area=next;draw();
});
$('videos').onchange=handle(event=>chooseVideo(event.target.value));
$('uploadForm').onsubmit=handle(async()=>{
  const file=$('file').files[0];
  if(!file)throw new Error('Choose a video.');
  if(file.size>250*1024*1024)throw new Error('Maximum video size is 250 MB.');
  $('uploadButton').disabled=true;notice('Uploading and reading recording…');
  try {
    const data=new FormData();data.append('file',file);
    const video=await api('/api/videos',{method:'POST',body:data});
    selectedVideo=video.id;await refreshVideos();await chooseVideo(video.id);
    notice('Recording ready. Mark the area and check the recording start time.');
  } finally { $('uploadButton').disabled=false; }
});
$('deleteVideo').onclick=handle(async()=>{
  if(!selectedVideo)return;
  if(!window.confirm('Delete this uploaded recording and its session findings? Export JSON first if needed.'))return;
  const deleting=selectedVideo;
  await api(`/api/videos/${deleting}`,{method:'DELETE'});
  if(job?.videoId===deleting)clearJob();
  await chooseVideo(null);await refreshVideos();await refreshJobs();notice('Recording and its session findings deleted.');
});
$('rulesForm').onsubmit=handle(async()=>{
  if(!selectedVideo)throw new Error('Upload and select a recording first.');
  const scheduled=$('schedule').checked,start=$('recordingStart').value;
  if(scheduled&&!start)throw new Error('Enter the actual recording start for time rules.');
  const rules={areaName:$('areaName').value,area,recordingStart:start?start+$('offset').value:null,
    fromTime:scheduled?$('from').value:null,toTime:scheduled?$('until').value:null,days:[],
    presenceSeconds:+$('presence').value,lingerSeconds:+$('linger').value,entryCount:+$('entries').value,
    entryWindowSeconds:+$('entryWindow').value,interactionSeconds:+$('interaction').value,
    proximity:.08,confidence:+$('confidence').value};
  $('analyseButton').disabled=true;
  try {
    job=await api('/api/jobs',json('POST',{videoId:selectedVideo,rules}));
    selectedJob=job.id;await refreshJobs();startPolling();
    notice('Analysis started. You can cancel it below.');
  } finally {syncButtons();}
});
function clearJob() {
  ++pollGeneration;clearTimeout(timer);selectedJob=null;job=null;stopAt=null;
  $('player').pause();$('player').removeAttribute('src');$('player').load();$('player').hidden=true;
  $('export').hidden=true;$('cancel').disabled=true;$('events').replaceChildren();
  $('progress').value=0;$('jobStatus').textContent='Choose an analysis.';
}
$('jobs').onchange=handle(event=>{clearJob();selectedJob=event.target.value||null;if(selectedJob)startPolling();});
$('cancel').onclick=handle(async()=>{if(selectedJob){await api(`/api/jobs/${selectedJob}/cancel`,{method:'POST'});notice('Cancellation requested.');}});
function time(seconds){const s=Math.floor(seconds);return [Math.floor(s/3600),Math.floor(s/60)%60,s%60].map(x=>String(x).padStart(2,'0')).join(':');}
function node(tag,text,cls){const el=document.createElement(tag);el.textContent=text;if(cls)el.className=cls;return el;}
function renderJob() {
  $('jobStatus').textContent=`${job.state} · ${job.stage}${job.error?' · '+job.error:''}`;
  $('progress').value=job.progress;
  $('cancel').disabled=!['QUEUED','RUNNING'].includes(job.state);
  $('export').href=`/api/jobs/${job.id}/export`;$('export').hidden=false;
  if(job.previewReady){
    const src=`/api/videos/${job.videoId}/preview.mp4`;
    if($('player').getAttribute('src')!==src){$('player').src=src;stopAt=null;}
    $('player').hidden=false;
  }
  const container=$('events');container.replaceChildren();
  if(!job.events.length)container.append(node('p',job.state==='COMPLETED'?'No events matched these rules. This does not establish that nothing happened.':'No findings yet.'));
  for(const event of job.events){
    const card=node('article','','event');
    card.append(node('div',event.type.replaceAll('_',' '),'pill'),node('h3',`${time(event.startSeconds)} – ${time(event.endSeconds)}`),node('p',event.reason));
    if(event.observedAt)card.append(node('p',`Recording time: ${event.observedAt}`,'muted'));
    card.append(node('p',`Latest person detection score: ${event.detectionConfidence.toFixed(2)} · Not a probability of wrongdoing`,'muted'));
    const controls=node('div','','row'),play=node('button','Play segment','secondary');play.disabled=!job.previewReady;
    play.onclick=handle(async()=>{
      const player=$('player');stopAt=event.endSeconds+3;
      player.currentTime=Math.max(0,event.startSeconds-3);await player.play();
      player.scrollIntoView({behavior:'smooth',block:'center'});
      $('playbackNote').textContent=`Playing ${time(Math.max(0,event.startSeconds-3))} – ${time(stopAt)}, including context.`;
    });
    const review=document.createElement('select');review.setAttribute('aria-label',`Review event at ${time(event.startSeconds)}`);
    for(const value of ['UNREVIEWED','RELEVANT','FALSE_ALERT','UNCERTAIN'])review.append(option(value,value.replaceAll('_',' ')));
    review.value=event.review;
    review.onchange=handle(async()=>{
      await api(`/api/jobs/${job.id}/events/${event.id}`,json('PUT',{review:review.value}));
      event.review=review.value;notice('Review saved for this session. Export JSON to keep it.');
    });
    controls.append(play,review);card.append(controls);container.append(card);
  }
}
$('player').addEventListener('timeupdate',()=>{if(stopAt!==null&&$('player').currentTime>=stopAt){$('player').pause();stopAt=null;}});
function startPolling() {
  clearTimeout(timer);const generation=++pollGeneration;
  async function tick(){
    try{
      const result=await api(`/api/jobs/${selectedJob}`);
      if(generation!==pollGeneration)return;
      job=result;renderJob();await refreshJobs();
      if(['QUEUED','RUNNING'].includes(job.state))timer=setTimeout(tick,1500);
    }catch(error){if(generation===pollGeneration){notice(error.message+' Re-select the analysis to retry.',true);}}
  }
  tick();
}
(async()=>{
  try{
    const status=await api('/api/status');ready=status.modelPresent&&status.videoToolsReady;
    notice(ready?'Setup ready. Upload a recording to begin.':`Setup needed: ${!status.videoToolsReady?'install FFmpeg/FFprobe. ':''}${!status.modelPresent?'Run scripts/setup-model.ps1. ':''}See the README, then refresh this page.`,!ready);
    await refreshVideos();await refreshJobs();draw();
    const running=jobList.find(j=>['QUEUED','RUNNING'].includes(j.state));
    if(running){selectedJob=running.id;$('jobs').value=running.id;startPolling();}
  }catch(error){notice(error.message,true);}
})();
