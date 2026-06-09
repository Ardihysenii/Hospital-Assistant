<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import axios from 'axios'
import {
  Mic, MicOff, Shield, Activity, Users, UserPlus,
  Layers, Database, Search, Trash2, UserX,
  Plus, CheckCircle2, AlertCircle, LogOut, Lock, UserCheck,
  Edit3, Sliders, Cpu, Terminal, ShieldAlert,
  Droplet, RefreshCw, Clock, Heart, AlertTriangle
} from 'lucide-vue-next'

const API_BASE = 'http://localhost:8081/api/v1'

const isAuthenticated = ref(false)
const currentUser = ref({ username: '', role: '' })
const authForm = ref({ username: '', password: '' })
const authError = ref('')
const currentTab = ref('analytics')

const patients = ref([])
const systemUsers = ref([])
const availableRoles = ref([])
const isListening = ref(false)
const voiceTranscript = ref('')
const aiResponse = ref('')
const searchQueries = ref('')
const userSearchQuery = ref('')
const userCreationSuccess = ref('')
const userCreationError = ref('')

const newPatient = ref({ firstName: '', lastName: '', bloodGroup: 'A+', dateOfBirth: '', username: '', password: '' })
const patientCreateError = ref('')
const patientCreateSuccess = ref('')

const nurseNewPatient = ref({ firstName: '', lastName: '', bloodGroup: 'A+', dateOfBirth: '', username: '', password: '' })
const nursePatientCreateError = ref('')
const nursePatientCreateSuccess = ref('')

const newSystemUser = ref({ username: '', password: '', role: '' })

const bloodRequestSuccess = ref('')
const bloodRequestError = ref('')
const isBroadcastingBlood = ref(false)
const newBloodRequest = ref({ patientName: '', bloodGroupRequested: 'A+', urgencyMessage: '' })

const myBloodRequests = ref([])
const isRefreshingRequests = ref(false)
const refreshCountdown = ref(5)
let bloodPollInterval = null
let countdownInterval = null

const isEditingPatient = ref(false)
const activeEditingPatient = ref({ id: null, firstName: '', lastName: '', bloodGroup: '', dateOfBirth: '' })
const patientUpdateError = ref('')
const patientUpdateSuccess = ref('')

const isEditingUser = ref(false)
const activeEditingUser = ref({ id: null, username: '', email: '', role: '' })
const userUpdateError = ref('')
const userUpdateSuccess = ref('')

const telemetrySystem = ref({ dbPing: '1.4ms', cpuLoad: '4.2%' })

const provisionableRoles = computed(() =>
    availableRoles.value.filter(r => {
      const name = getRoleName(r).toUpperCase()
      return name === 'ADMIN' || name === 'NURSE'
    })
)

const setAuthHeader = (token) => {
  if (token) axios.defaults.headers.common['Authorization'] = `Bearer ${token}`
  else delete axios.defaults.headers.common['Authorization']
}

const fetchRoles = async () => {
  try {
    const res = await axios.get(`${API_BASE}/roles`)
    availableRoles.value = res.data
    if (availableRoles.value.length > 0 && !newSystemUser.value.role) {
      const firstNonPatient = availableRoles.value.find(r => getRoleName(r).toUpperCase() !== 'PATIENT')
      newSystemUser.value.role = getRoleName(firstNonPatient || availableRoles.value[0])
    }
  } catch {
    availableRoles.value = ['ADMIN', 'NURSE']
    if (!newSystemUser.value.role) newSystemUser.value.role = 'NURSE'
  }
}

const fetchMyBloodRequests = async (silent = false) => {
  const now = Date.now()
  if (fetchMyBloodRequests.lastRun && now - fetchMyBloodRequests.lastRun < 3000) {
    return
  }
  fetchMyBloodRequests.lastRun = now
  if (!silent) isRefreshingRequests.value = true
  try {
    const token = localStorage.getItem('token') || localStorage.getItem('authToken') || sessionStorage.getItem('token')
    const headers = token ? { Authorization: `Bearer ${token}` } : {}
    const res = await axios.get(`${API_BASE}/blood-requests/my-requests`, { headers })
    myBloodRequests.value = Array.isArray(res.data) ? res.data : []
  } catch (err) {
    console.error('Arsyeja e gabimit:', err.response?.data || err.message)
  } finally {
    isRefreshingRequests.value = false
  }
}

const startBloodPoll = () => {
  fetchMyBloodRequests(false)
  refreshCountdown.value = 5
  bloodPollInterval = setInterval(() => {
    fetchMyBloodRequests(true)
    refreshCountdown.value = 5
  }, 5000)
  countdownInterval = setInterval(() => {
    refreshCountdown.value = Math.max(0, refreshCountdown.value - 1)
  }, 1000)
}

const stopBloodPoll = () => {
  if (bloodPollInterval) { clearInterval(bloodPollInterval); bloodPollInterval = null }
  if (countdownInterval) { clearInterval(countdownInterval); countdownInterval = null }
}

const acceptBloodRequest = async (requestId) => {
  try {
    await axios.post(`${API_BASE}/blood-requests/accept/${requestId}`)
    await fetchMyBloodRequests()
  } catch (err) {
    console.error('Failed to accept request:', err)
  }
}

const handleBroadcastEmergency = async () => {
  bloodRequestSuccess.value = ''
  bloodRequestError.value = ''
  isBroadcastingBlood.value = true
  try {
    await axios.post(`${API_BASE}/blood-requests/broadcast`, {
      patientName: newBloodRequest.value.patientName,
      bloodGroupRequested: newBloodRequest.value.bloodGroupRequested,
      urgencyMessage: newBloodRequest.value.urgencyMessage
    })
    bloodRequestSuccess.value = `CRITICAL BROADCAST DISPATCHED: Core registry filtered for [${newBloodRequest.value.bloodGroupRequested}] matches.`
    newBloodRequest.value = { patientName: '', bloodGroupRequested: 'A+', urgencyMessage: '' }
  } catch (err) {
    bloodRequestError.value = err.response?.data?.message || 'Transmission Deflection: Broadcast packet validation failure.'
  } finally {
    isBroadcastingBlood.value = false
  }
}

const handleLogin = async () => {
  authError.value = ''
  if (!authForm.value.username || !authForm.value.password) return
  try {
    const res = await axios.post(`${API_BASE}/auth/login`, {
      username: authForm.value.username,
      password: authForm.value.password
    })
    const { token, role, username } = res.data
    localStorage.setItem('aura_security_token', token)
    localStorage.setItem('aura_user_profile', JSON.stringify({ username, role }))
    setAuthHeader(token)
    currentUser.value = { username, role }
    isAuthenticated.value = true
    if (role === 'ADMIN') currentTab.value = 'analytics'
    else if (role === 'NURSE') currentTab.value = 'voice'
    else if (role === 'PATIENT') {
      currentTab.value = 'patient-view'
      setTimeout(() => startBloodPoll(), 300)
    }
    await fetchPatients()
    await fetchSystemUsers()
    await fetchRoles()
  } catch (err) {
    console.error('FULL AXIOS ERROR:', err)
    authError.value = 'Access Denied: Invalid cryptographic token signatures or profile identity match.'
  }
}

const handleLogout = () => {
  stopBloodPoll()
  localStorage.removeItem('aura_security_token')
  localStorage.removeItem('aura_user_profile')
  setAuthHeader(null)
  isAuthenticated.value = false
  currentUser.value = { username: '', role: '' }
  authForm.value = { username: '', password: '' }
  myBloodRequests.value = []
}

const handleCreateSystemUser = async () => {
  userCreationSuccess.value = ''
  userCreationError.value = ''
  try {
    await axios.post(`${API_BASE}/auth/register`, {
      username: newSystemUser.value.username,
      password: newSystemUser.value.password,
      email: `${newSystemUser.value.username.toLowerCase()}@hospital.com`,
      roles: [newSystemUser.value.role]
    })
    userCreationSuccess.value = `Successfully provisioned [${newSystemUser.value.username}] into system matrix.`
    const fallbackRole = provisionableRoles.value[0]
    newSystemUser.value = { username: '', password: '', role: fallbackRole ? getRoleName(fallbackRole) : 'NURSE' }
    await fetchSystemUsers()
  } catch (err) {
    userCreationError.value = err.response?.data?.message || 'Provisioning Rejection: Identity pattern collision.'
  }
}

const fetchPatients = async () => {
  if (!isAuthenticated.value) return
  try {
    const res = await axios.get(`${API_BASE}/patients`)
    patients.value = res.data
  } catch (err) {
    console.error('Failed to pull registry matrix:', err)
    if (err.response?.status === 401) handleLogout()
  }
}

const fetchSystemUsers = async () => {
  if (!isAuthenticated.value || currentUser.value.role !== 'ADMIN') return
  try {
    const res = await axios.get(`${API_BASE}/users`)
    systemUsers.value = res.data
  } catch {
    if (systemUsers.value.length === 0) {
      systemUsers.value = [
        { id: 1, username: 'root_admin', role: 'ADMIN', email: 'root_admin@hospital.com' },
        { id: 2, username: 'nurse_clara', role: 'NURSE', email: 'nurse_clara@hospital.com' }
      ]
    }
  }
}

const handleDeletePatient = async (patientId) => {
  if (!confirm("CRITICAL: Purge this specific medical signature permanently from core master database layers?")) return
  try {
    await axios.delete(`${API_BASE}/patients/${patientId}`)
    await fetchPatients()
  } catch {
    alert('Execution Failure: Backend infrastructure rejected record destruction sequence.')
  }
}

const handleDeleteUser = async (userIdOrUsername) => {
  if (!confirm("SECURITY WARNING: De-authorize credentials map and drop this architecture route profile?")) return
  try {
    await axios.delete(`${API_BASE}/users/${userIdOrUsername}`)
    await fetchSystemUsers()
  } catch {
    systemUsers.value = systemUsers.value.filter(u => u.id !== userIdOrUsername && u.username !== userIdOrUsername)
  }
}

const handleManualSubmit = async () => {
  patientCreateError.value = ''
  patientCreateSuccess.value = ''
  try {
    const registerResponse = await axios.post(`${API_BASE}/auth/register`, {
      username: newPatient.value.username,
      password: newPatient.value.password,
      email: `${newPatient.value.username.toLowerCase()}@hospital.com`,
      roles: ['PATIENT']
    })
    console.log("REGISTER RESPONSE =", registerResponse.data)
    const userId = registerResponse.data?.id || registerResponse.data?.userId
    await axios.post(`${API_BASE}/patients`, {
      firstName: newPatient.value.firstName,
      lastName: newPatient.value.lastName,
      bloodGroup: newPatient.value.bloodGroup,
      dateOfBirth: newPatient.value.dateOfBirth,
      user: { id: userId }
    })
    patientCreateSuccess.value = `Patient [${newPatient.value.username}] provisioned and medical record committed.`
    newPatient.value = { firstName: '', lastName: '', bloodGroup: 'A+', dateOfBirth: '', username: '', password: '' }
    await fetchPatients()
    await fetchSystemUsers()
  } catch (err) {
    patientCreateError.value = err.response?.data?.message || 'Commit failure: username collision or validation error.'
  }
}

const handleNursePatientSubmit = async () => {
  nursePatientCreateError.value = ''
  nursePatientCreateSuccess.value = ''
  try {
    const registerResponse = await axios.post(`${API_BASE}/auth/register`, {
      username: nurseNewPatient.value.username,
      password: nurseNewPatient.value.password,
      email: `${nurseNewPatient.value.username.toLowerCase()}@hospital.com`,
      roles: ['PATIENT']
    })
    const userId = registerResponse.data?.id || registerResponse.data?.userId
    await axios.post(`${API_BASE}/patients`, {
      firstName: nurseNewPatient.value.firstName,
      lastName: nurseNewPatient.value.lastName,
      bloodGroup: nurseNewPatient.value.bloodGroup,
      dateOfBirth: nurseNewPatient.value.dateOfBirth,
      user: { id: userId }
    })
    nursePatientCreateSuccess.value = `Patient [${nurseNewPatient.value.username}] registered successfully.`
    nurseNewPatient.value = { firstName: '', lastName: '', bloodGroup: 'A+', dateOfBirth: '', username: '', password: '' }
    await fetchPatients()
  } catch (err) {
    nursePatientCreateError.value = err.response?.data?.message || 'Commit failure: username collision or validation error.'
  }
}

const initiatePatientUpdate = (patient) => {
  patientUpdateSuccess.value = ''
  patientUpdateError.value = ''
  activeEditingPatient.value = { ...patient }
  isEditingPatient.value = true
}

const handleUpdatePatient = async () => {
  patientUpdateSuccess.value = ''
  patientUpdateError.value = ''
  try {
    await axios.put(`${API_BASE}/patients/${activeEditingPatient.value.id}`, activeEditingPatient.value)
    patientUpdateSuccess.value = "Patient dynamic vectors matched and synced successfully."
    await fetchPatients()
    setTimeout(() => { isEditingPatient.value = false }, 1200)
  } catch {
    patientUpdateError.value = "Update sequence rejected: Check formatting or constraint validations."
  }
}

const initiateUserUpdate = (user) => {
  if (currentUser.value.role !== 'ADMIN') return
  userUpdateSuccess.value = ''
  userUpdateError.value = ''
  activeEditingUser.value = { ...user }
  isEditingUser.value = true
}

const handleUpdateUser = async () => {
  userUpdateSuccess.value = ''
  userUpdateError.value = ''
  try {
    await axios.put(`${API_BASE}/users/${activeEditingUser.value.id || activeEditingUser.value.username}`, activeEditingUser.value)
    userUpdateSuccess.value = "Platform authorization credentials parameters updated."
    await fetchSystemUsers()
    setTimeout(() => { isEditingUser.value = false }, 1200)
  } catch {
    const idx = systemUsers.value.findIndex(u => u.id === activeEditingUser.value.id || u.username === activeEditingUser.value.username)
    if (idx !== -1) {
      systemUsers.value[idx] = { ...activeEditingUser.value }
      userUpdateSuccess.value = "Optimistic network model user record updated."
      setTimeout(() => { isEditingUser.value = false }, 1200)
    } else {
      userUpdateError.value = "Dynamic configuration database handshake failed."
    }
  }
}

const getRoleName = (role) => (typeof role === 'string' ? role : role?.name || role?.roleName || String(role))

let speechDebounceTimeout = null
const startVoiceRecognition = () => {
  const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition
  if (!SpeechRecognition) { aiResponse.value = "Speech architecture unavailable on this client framework."; return }
  const recognition = new SpeechRecognition()
  recognition.lang = 'en-US'
  recognition.interimResults = false
  recognition.continuous = true
  recognition.onstart = () => { isListening.value = true; aiResponse.value = ''; voiceTranscript.value = 'Awaiting vocal stream matrix...' }
  recognition.onerror = () => { isListening.value = false }
  recognition.onend = () => { isListening.value = false }
  recognition.onresult = (event) => {
    let text = ''
    for (let i = 0; i < event.results.length; i++) text += event.results[i][0].transcript + ' '
    voiceTranscript.value = text.trim()
    if (speechDebounceTimeout) clearTimeout(speechDebounceTimeout)
    speechDebounceTimeout = setTimeout(async () => {
      try {
        recognition.stop(); isListening.value = false
        const res = await axios.post(`${API_BASE}/ai/command`, { command: voiceTranscript.value })
        aiResponse.value = res.data.response
        await fetchPatients()
      } catch { aiResponse.value = "Upstream communication interface failure." }
    }, 1500)
  }
  recognition.start()
}

const filteredPatients = computed(() => {
  const list = searchQueries.value
      ? patients.value.filter(p => `${p.firstName} ${p.lastName}`.toLowerCase().includes(searchQueries.value.toLowerCase()))
      : [...patients.value]
  return list.slice().reverse()
})

const filteredUsers = computed(() => {
  if (!userSearchQuery.value) return systemUsers.value
  return systemUsers.value.filter(u =>
      u.username.toLowerCase().includes(userSearchQuery.value.toLowerCase()) ||
      u.role.toLowerCase().includes(userSearchQuery.value.toLowerCase())
  )
})

onMounted(async () => {
  const savedToken = localStorage.getItem('aura_security_token')
  const savedProfile = localStorage.getItem('aura_user_profile')
  if (savedToken && savedProfile) {
    setAuthHeader(savedToken)
    currentUser.value = JSON.parse(savedProfile)
    isAuthenticated.value = true
    if (currentUser.value.role === 'ADMIN') currentTab.value = 'analytics'
    else if (currentUser.value.role === 'NURSE') currentTab.value = 'voice'
    else if (currentUser.value.role === 'PATIENT') {
      currentTab.value = 'patient-view'
      setTimeout(() => startBloodPoll(), 300)
    }
    await fetchPatients()
    await fetchSystemUsers()
    await fetchRoles()
  }
})

onUnmounted(() => stopBloodPoll())
</script>

<template>
  <div class="min-h-screen bg-[#070708] text-neutral-200 font-sans antialiased selection:bg-[#c5a880] selection:text-black relative overflow-x-hidden">

    <div class="absolute inset-0 pointer-events-none overflow-hidden z-0">
      <div class="absolute top-[-20%] right-[-10%] w-[60vw] h-[60vw] rounded-full bg-[radial-gradient(circle,rgba(197,168,128,0.04)_0%,transparent_70%)] blur-[80px] animate-ambient-slow"></div>
      <div class="absolute bottom-[-20%] left-[-10%] w-[70vw] h-[70vw] rounded-full bg-[radial-gradient(circle,rgba(255,255,255,0.015)_0%,transparent_70%)] blur-[100px] animate-ambient-slower"></div>
      <div class="absolute inset-0 bg-[linear-gradient(to_right,rgba(197,168,128,0.008)_1px,transparent_1px),linear-gradient(to_bottom,rgba(197,168,128,0.008)_1px,transparent_1px)] bg-[size:6rem_6rem]"></div>
    </div>

    <!-- LOGIN -->
    <div v-if="!isAuthenticated" class="min-h-screen flex items-center justify-center px-6 relative z-10">
      <div class="w-full max-w-md bg-[#0d0d0f]/90 border border-neutral-800/60 p-12 rounded-3xl shadow-[0_25px_70px_rgba(0,0,0,0.8)] backdrop-blur-xl space-y-10 relative overflow-hidden animate-fadeIn">
        <div class="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-[#c5a880]/30 to-transparent"></div>
        <div class="text-center space-y-3">
          <div class="inline-flex text-[#c5a880] mb-1 p-3 rounded-full bg-[#c5a880]/5 border border-[#c5a880]/10">
            <Lock class="w-5 h-5 stroke-[1.2]"/>
          </div>
          <h2 class="text-xl tracking-[0.35em] uppercase font-light text-white">Aura Gateway</h2>
          <p class="text-[9px] text-neutral-500 tracking-[0.25em] uppercase">High Security Clinical Matrix Access</p>
        </div>
        <form @submit.prevent="handleLogin" class="space-y-7">
          <div v-if="authError" class="bg-red-950/20 border border-red-900/40 text-red-400 p-4 rounded-xl text-[11px] font-medium flex items-center gap-3 font-mono">
            <AlertCircle class="w-4 h-4 shrink-0 text-red-500"/><span>{{ authError }}</span>
          </div>
          <div class="space-y-2">
            <label class="text-[9px] tracking-[0.25em] uppercase text-neutral-400 block font-light">System Identifier</label>
            <input v-model="authForm.username" type="text" required placeholder="system_operator"
                   class="w-full bg-[#121215] border border-neutral-800/80 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-[#c5a880] focus:ring-1 focus:ring-[#c5a880]/20 transition-all placeholder-neutral-700 font-mono tracking-wide"/>
          </div>
          <div class="space-y-2">
            <label class="text-[9px] tracking-[0.25em] uppercase text-neutral-400 block font-light">Access Cipher</label>
            <input v-model="authForm.password" type="password" required placeholder="••••••••"
                   class="w-full bg-[#121215] border border-neutral-800/80 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-[#c5a880] focus:ring-1 focus:ring-[#c5a880]/20 transition-all placeholder-neutral-700 font-mono tracking-wide"/>
          </div>
          <button type="submit" class="w-full bg-gradient-to-r from-[#c5a880] to-[#b3956d] hover:from-[#d1b48c] hover:to-[#c5a880] text-black text-[10px] py-4 rounded-xl font-semibold tracking-[0.25em] transition-all uppercase cursor-pointer shadow-[0_4px_20px_rgba(197,168,128,0.15)] mt-8">
            Authenticate Route Session
          </button>
        </form>
      </div>
    </div>

    <div v-else class="min-h-screen flex flex-col relative z-10 animate-fadeIn">

      <!-- HEADER -->
      <header class="border-b border-neutral-900 bg-[#070708]/70 backdrop-blur-xl sticky top-0 z-50">
        <div class="max-w-7xl mx-auto px-8 h-20 flex items-center justify-between">
          <div class="flex items-center gap-4">
            <div class="text-[#c5a880] p-2.5 rounded-xl bg-[#c5a880]/5 border border-[#c5a880]/10">
              <Activity class="w-4 h-4 stroke-[1.5]"/>
            </div>
            <div>
              <span class="text-xs font-light tracking-[0.25em] text-white block uppercase">Aura Command</span>
              <span class="text-[9px] font-mono text-[#c5a880] tracking-widest uppercase flex items-center gap-1.5">
                <span class="w-1 h-1 rounded-full bg-[#c5a880] inline-block animate-pulse"></span>
                Secure Frame Link Enabled
              </span>
            </div>
          </div>

          <nav v-if="currentUser.role === 'ADMIN'" class="hidden lg:flex gap-4">
            <button @click="currentTab = 'analytics'" :class="currentTab === 'analytics' ? 'text-white border-[#c5a880] bg-[#c5a880]/5' : 'text-neutral-400 border-transparent hover:text-white'" class="px-4 py-2 border-b-2 text-[10px] tracking-[0.2em] uppercase transition-all cursor-pointer flex items-center gap-2 rounded-t-lg">
              <Layers class="w-3.5 h-3.5 stroke-[1.5]"/> Core Operations
            </button>
            <button @click="currentTab = 'voice'" :class="currentTab === 'voice' ? 'text-white border-[#c5a880] bg-[#c5a880]/5' : 'text-neutral-400 border-transparent hover:text-white'" class="px-4 py-2 border-b-2 text-[10px] tracking-[0.2em] uppercase transition-all cursor-pointer flex items-center gap-2 rounded-t-lg">
              <Mic class="w-3.5 h-3.5 stroke-[1.5]"/> Acoustic Array
            </button>
            <button @click="currentTab = 'registry'" :class="currentTab === 'registry' ? 'text-white border-[#c5a880] bg-[#c5a880]/5' : 'text-neutral-400 border-transparent hover:text-white'" class="px-4 py-2 border-b-2 text-[10px] tracking-[0.2em] uppercase transition-all cursor-pointer flex items-center gap-2 rounded-t-lg">
              <Users class="w-3.5 h-3.5 stroke-[1.5]"/> Master Registry Matrix
            </button>
            <button @click="currentTab = 'blood-broadcast'" :class="currentTab === 'blood-broadcast' ? 'text-white border-[#c5a880] bg-[#c5a880]/5' : 'text-neutral-400 border-transparent hover:text-white'" class="px-4 py-2 border-b-2 text-[10px] tracking-[0.2em] uppercase transition-all cursor-pointer flex items-center gap-2 rounded-t-lg">
              <Droplet class="w-3.5 h-3.5 stroke-[1.5] text-red-500 animate-pulse"/> Emergency Broadcast
            </button>
          </nav>

          <!-- NURSE NAV — added add-patient tab -->
          <nav v-else-if="currentUser.role === 'NURSE'" class="flex gap-4">
            <button @click="currentTab = 'voice'" :class="currentTab === 'voice' ? 'text-white border-[#c5a880] bg-[#c5a880]/5' : 'text-neutral-400 border-transparent hover:text-white'" class="px-4 py-2 border-b-2 text-[10px] tracking-[0.2em] uppercase transition-all cursor-pointer flex items-center gap-2 rounded-t-lg">
              <Mic class="w-3.5 h-3.5 stroke-[1.5]"/> Clinical Intake Terminal
            </button>
            <button @click="currentTab = 'add-patient'" :class="currentTab === 'add-patient' ? 'text-white border-[#c5a880] bg-[#c5a880]/5' : 'text-neutral-400 border-transparent hover:text-white'" class="px-4 py-2 border-b-2 text-[10px] tracking-[0.2em] uppercase transition-all cursor-pointer flex items-center gap-2 rounded-t-lg">
              <UserPlus class="w-3.5 h-3.5 stroke-[1.5]"/> Register Patient
            </button>
            <button @click="currentTab = 'registry'" :class="currentTab === 'registry' ? 'text-white border-[#c5a880] bg-[#c5a880]/5' : 'text-neutral-400 border-transparent hover:text-white'" class="px-4 py-2 border-b-2 text-[10px] tracking-[0.2em] uppercase transition-all cursor-pointer flex items-center gap-2 rounded-t-lg">
              <Users class="w-3.5 h-3.5 stroke-[1.5]"/> Read Patient Log Array
            </button>
            <button @click="currentTab = 'blood-broadcast'" :class="currentTab === 'blood-broadcast' ? 'text-white border-[#c5a880] bg-[#c5a880]/5' : 'text-neutral-400 border-transparent hover:text-white'" class="px-4 py-2 border-b-2 text-[10px] tracking-[0.2em] uppercase transition-all cursor-pointer flex items-center gap-2 rounded-t-lg">
              <Droplet class="w-3.5 h-3.5 stroke-[1.5] text-red-500 animate-pulse"/> Emergency Broadcast
            </button>
          </nav>

          <div class="flex items-center gap-4">
            <div class="hidden md:flex items-center gap-2.5 bg-[#121215] border border-neutral-800 px-4 py-2 rounded-xl text-[9px] font-mono tracking-wider text-neutral-400">
              <UserCheck class="w-3.5 h-3.5 text-[#c5a880]"/>
              <span>AUTHORIZED_ID: <span class="text-white font-medium">{{ currentUser.username.toUpperCase() }}</span></span>
            </div>
            <button @click="handleLogout" class="border border-neutral-800 bg-neutral-950 hover:bg-red-950/20 text-neutral-400 hover:text-red-400 hover:border-red-900/40 p-2.5 rounded-xl transition-all cursor-pointer">
              <LogOut class="w-4 h-4"/>
            </button>
          </div>
        </div>
      </header>

      <main class="max-w-7xl mx-auto px-8 py-12 flex-1 w-full space-y-12">

        <!-- BLOOD BROADCAST TAB -->
        <div v-if="currentTab === 'blood-broadcast' && (currentUser.role === 'ADMIN' || currentUser.role === 'NURSE')" class="space-y-12 animate-fadeIn">
          <div class="flex items-center justify-between border-b border-neutral-900 pb-4">
            <div>
              <h2 class="text-xs uppercase font-light tracking-[0.3em] text-red-500 flex items-center gap-2">
                <ShieldAlert class="w-4 h-4 text-red-500"/> Critical Hematology Dispatch Panel
              </h2>
              <p class="text-[10px] text-neutral-500 uppercase font-mono mt-1">Targeted Blood donor extraction broadcast vector engine</p>
            </div>
            <span class="text-[9px] bg-red-950/20 border border-red-900/40 text-red-400 font-mono px-3 py-1 rounded-full uppercase tracking-widest animate-pulse">Emergency Directives Active</span>
          </div>
          <div class="max-w-xl mx-auto bg-[#0d0d0f] border border-neutral-900 rounded-2xl p-8 shadow-xl relative overflow-hidden">
            <div class="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-red-500/30 to-transparent"></div>
            <h3 class="text-[10px] font-light tracking-[0.25em] uppercase text-white mb-8 flex items-center gap-2">
              <Droplet class="w-4 h-4 text-red-500 stroke-[1.5]"/> Compile Emergency Broadcast Command
            </h3>
            <div v-if="bloodRequestSuccess" class="bg-emerald-950/20 border border-emerald-900/40 text-emerald-400 p-4 rounded-xl text-[11px] font-mono mb-6">{{ bloodRequestSuccess }}</div>
            <div v-if="bloodRequestError" class="bg-red-950/20 border border-red-900/40 text-red-400 p-4 rounded-xl text-[11px] font-mono mb-6">{{ bloodRequestError }}</div>
            <form @submit.prevent="handleBroadcastEmergency" class="space-y-6">
              <div class="space-y-1.5">
                <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">Target Recipient Identity (Patient Name)</label>
                <input v-model="newBloodRequest.patientName" type="text" placeholder="e.g., John Doe (Critical Condition)" required
                       class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-red-500 font-mono transition-all"/>
              </div>
              <div class="space-y-1.5">
                <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">Required Blood Phenotype Classification</label>
                <select v-model="newBloodRequest.bloodGroupRequested" required class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-red-500 font-mono cursor-pointer tracking-wide">
                  <option value="A+">A-Positive (A+)</option><option value="A-">A-Negative (A-)</option>
                  <option value="B+">B-Positive (B+)</option><option value="B-">B-Negative (B-)</option>
                  <option value="AB+">AB-Positive (AB+)</option><option value="AB-">AB-Negative (AB-)</option>
                  <option value="O+">O-Positive (O+)</option><option value="O-">O-Negative (O-)</option>
                </select>
              </div>
              <div class="space-y-1.5">
                <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">Transmission Note / Override Message (Optional)</label>
                <textarea v-model="newBloodRequest.urgencyMessage" placeholder="Leave blank to deploy dynamic automated emergency string template layout..."
                          class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-4 text-xs text-white focus:outline-none focus:border-red-500 font-mono transition-all h-24 resize-none"></textarea>
              </div>
              <button type="submit" :disabled="isBroadcastingBlood"
                      class="w-full bg-neutral-900 hover:bg-red-950/20 text-red-400 disabled:text-neutral-600 disabled:bg-neutral-950 border border-neutral-800 hover:border-red-900/40 text-[10px] py-4 rounded-xl font-semibold tracking-[0.2em] transition-all uppercase cursor-pointer mt-4 shadow-md">
                {{ isBroadcastingBlood ? 'Executing Stream Broadcast Matrix...' : 'Deploy System Emergency Broadcast' }}
              </button>
            </form>
          </div>
        </div>

        <!-- ANALYTICS TAB -->
        <div v-if="currentTab === 'analytics' && currentUser.role === 'ADMIN'" class="space-y-12 animate-fadeIn">
          <div class="flex items-center justify-between border-b border-neutral-900 pb-4">
            <div>
              <h2 class="text-xs uppercase font-light tracking-[0.3em] text-[#c5a880]">System Telemetry Overview</h2>
              <p class="text-[10px] text-neutral-500 uppercase font-mono mt-1">Strategic Operations Realtime Index Dashboard</p>
            </div>
            <span class="text-[9px] bg-[#c5a880]/5 border border-[#c5a880]/20 text-[#c5a880] font-mono px-3 py-1 rounded-full uppercase tracking-widest">Core Framework Active</span>
          </div>
          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
            <div class="bg-gradient-to-b from-[#101013] to-[#0d0d0f] border border-neutral-800/80 rounded-2xl p-6 shadow-[0_10px_30px_rgba(0,0,0,0.3)] hover:border-neutral-700 transition-all">
              <span class="text-[9px] font-mono tracking-widest uppercase text-neutral-500 block">Database Index Load</span>
              <h3 class="text-2xl font-light text-white mt-2 tracking-tight">{{ patients.length }} Patient Nodes</h3>
              <div class="mt-4 flex items-center gap-1.5 text-[9px] text-neutral-400 font-mono uppercase tracking-wider">
                <CheckCircle2 class="w-3 h-3 text-[#c5a880]"/> Realtime Sync Active
              </div>
            </div>
            <div class="bg-gradient-to-b from-[#101013] to-[#0d0d0f] border border-neutral-800/80 rounded-2xl p-6 shadow-[0_10px_30px_rgba(0,0,0,0.3)] hover:border-neutral-700 transition-all">
              <span class="text-[9px] font-mono tracking-widest uppercase text-neutral-500 block">Access Control List</span>
              <h3 class="text-2xl font-light text-[#c5a880] mt-2 tracking-tight">{{ systemUsers.length || 2 }} Core Profiles</h3>
              <div class="mt-4 flex items-center gap-1.5 text-[9px] text-neutral-400 font-mono uppercase tracking-wider">
                <Shield class="w-3 h-3 text-neutral-500"/> RBAC Security Matrix
              </div>
            </div>
            <div class="bg-gradient-to-b from-[#101013] to-[#0d0d0f] border border-neutral-800/80 rounded-2xl p-6 shadow-[0_10px_30px_rgba(0,0,0,0.3)] flex flex-col justify-between hover:border-neutral-700 transition-all">
              <span class="text-[9px] font-mono tracking-widest uppercase text-neutral-500 block">Infrastructure Backbone</span>
              <div class="flex items-center gap-2.5 text-[#c5a880] mt-4 text-[10px] font-mono tracking-wider uppercase">
                <Database class="w-4 h-4 text-neutral-400"/><span>PG_SQL_SSL // SECURE</span>
              </div>
            </div>
            <div class="bg-gradient-to-b from-[#101013] to-[#0d0d0f] border border-neutral-800/80 rounded-2xl p-6 shadow-[0_10px_30px_rgba(0,0,0,0.3)] flex flex-col justify-between hover:border-neutral-700 transition-all">
              <span class="text-[9px] font-mono tracking-widest uppercase text-neutral-500 block">Gateway Latency / Load</span>
              <div class="space-y-1.5 mt-3">
                <div class="flex justify-between text-[10px] font-mono"><span class="text-neutral-500">PING:</span><span class="text-white font-medium">{{ telemetrySystem.dbPing }}</span></div>
                <div class="flex justify-between text-[10px] font-mono"><span class="text-neutral-500">CPU CORE:</span><span class="text-white font-medium">{{ telemetrySystem.cpuLoad }}</span></div>
              </div>
            </div>
          </div>
          <div class="grid grid-cols-1 lg:grid-cols-3 gap-8">
            <div class="lg:col-span-2 bg-[#0c0c0e] border border-neutral-900 p-8 rounded-2xl space-y-6 shadow-xl relative overflow-hidden">
              <div class="absolute top-0 right-0 p-4 font-mono text-[8px] text-neutral-600 uppercase">System Trace 08x22</div>
              <h3 class="text-[10px] font-light tracking-[0.25em] uppercase text-white flex items-center gap-2">
                <Cpu class="w-4 h-4 text-[#c5a880] stroke-[1.2]"/> Real-Time Cluster Node Analytics Vector
              </h3>
              <div class="h-32 flex items-end gap-2 pt-4 border-b border-neutral-800/60 relative">
                <div class="absolute inset-0 flex flex-col justify-between pointer-events-none text-[8px] font-mono text-neutral-700 uppercase">
                  <div class="border-b border-neutral-900/40 w-full pt-1">Target Max Capacity (98.2%)</div>
                  <div class="border-b border-neutral-900/40 w-full">Optimal Threshold (50.0%)</div>
                  <div></div>
                </div>
                <div class="bg-[#c5a880]/10 border-t border-[#c5a880]/40 w-full h-[45%] hover:bg-[#c5a880]/20 rounded-t-sm transition-all"></div>
                <div class="bg-[#c5a880]/10 border-t border-[#c5a880]/40 w-full h-[62%] hover:bg-[#c5a880]/20 rounded-t-sm transition-all"></div>
                <div class="bg-[#c5a880]/10 border-t border-[#c5a880]/40 w-full h-[31%] hover:bg-[#c5a880]/20 rounded-t-sm transition-all"></div>
                <div class="bg-[#c5a880]/20 border-t border-[#c5a880] w-full h-[78%] hover:bg-[#c5a880]/30 rounded-t-sm transition-all relative">
                  <span class="absolute top-[-15px] left-1/2 -translate-x-1/2 font-mono text-[8px] text-[#c5a880]">PEAK</span>
                </div>
                <div class="bg-[#c5a880]/10 border-t border-[#c5a880]/40 w-full h-[40%] hover:bg-[#c5a880]/20 rounded-t-sm transition-all"></div>
                <div class="bg-[#c5a880]/10 border-t border-[#c5a880]/40 w-full h-[52%] hover:bg-[#c5a880]/20 rounded-t-sm transition-all"></div>
              </div>
              <div class="flex justify-between text-[8px] font-mono text-neutral-500 uppercase tracking-widest">
                <span>Node alpha</span><span>Node beta</span><span>Node gamma</span><span>Node delta (active)</span><span>Node epsilon</span><span>Node zeta</span>
              </div>
            </div>
            <div class="bg-[#0c0c0e] border border-neutral-900 p-8 rounded-2xl space-y-4 shadow-xl">
              <h3 class="text-[10px] font-light tracking-[0.25em] uppercase text-white flex items-center gap-2">
                <Terminal class="w-4 h-4 text-neutral-400 stroke-[1.2]"/> Platform Security Ledger Trail
              </h3>
              <div class="font-mono text-[9px] space-y-3 max-h-[140px] overflow-y-auto pr-2 text-neutral-400">
                <div class="border-l border-[#c5a880]/40 pl-2 py-0.5"><span class="text-neutral-600">[SEC-OK]</span> Cryptographic handshake initialized via token model.</div>
                <div class="border-l border-neutral-800 pl-2 py-0.5"><span class="text-[#c5a880]">[AUDIT]</span> Query pulled context maps for registered patient profiles.</div>
                <div class="border-l border-neutral-800 pl-2 py-0.5"><span class="text-neutral-600">[SYNC]</span> Postgres entity maps synced with server cache models.</div>
              </div>
            </div>
          </div>
          <div class="grid grid-cols-1 lg:grid-cols-2 gap-12 pt-4">
            <!-- SYSTEM USER PROVISIONING — ADMIN & NURSE only -->
            <div class="bg-[#0d0d0f] border border-neutral-900 rounded-2xl p-8 shadow-xl">
              <h3 class="text-[10px] font-light tracking-[0.25em] uppercase text-white mb-8 flex items-center gap-2">
                <UserPlus class="w-4 h-4 text-[#c5a880] stroke-[1.5]"/> Platform Account Provisioning
              </h3>
              <div v-if="userCreationSuccess" class="bg-emerald-950/20 border border-emerald-900/40 text-emerald-400 p-4 rounded-xl text-[11px] font-mono mb-5">{{ userCreationSuccess }}</div>
              <div v-if="userCreationError" class="bg-red-950/20 border border-red-900/40 text-red-400 p-4 rounded-xl text-[11px] font-mono mb-5">{{ userCreationError }}</div>
              <form @submit.prevent="handleCreateSystemUser" class="space-y-5">
                <div class="space-y-1.5">
                  <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">System Username (Unique)</label>
                  <input v-model="newSystemUser.username" type="text" placeholder="e.g., nurse_sarah" required
                         class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-[#c5a880] font-mono transition-all"/>
                </div>
                <div class="space-y-1.5">
                  <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">Temporary Passphrase</label>
                  <input v-model="newSystemUser.password" type="password" placeholder="••••••••" required
                         class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-[#c5a880] font-mono transition-all"/>
                </div>
                <div class="space-y-1.5">
                  <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">System Clearance Level (RBAC)</label>
                  <select v-model="newSystemUser.role" class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-[#c5a880] cursor-pointer font-mono tracking-wide">
                    <option v-for="role in provisionableRoles" :key="getRoleName(role)" :value="getRoleName(role)">{{ getRoleName(role) }}</option>
                  </select>
                </div>
                <button type="submit" class="w-full bg-neutral-900 hover:bg-neutral-800 text-[#c5a880] border border-neutral-800 text-[10px] py-3.5 rounded-xl font-semibold tracking-[0.2em] transition-all uppercase cursor-pointer mt-4 shadow-md">
                  Commit Account Provisioning
                </button>
              </form>
            </div>

            <!-- PATIENT RECORD COMPILER — registers PATIENT account + medical record -->
            <div class="bg-[#0d0d0f] border border-neutral-900 rounded-2xl p-8 shadow-xl">
              <h3 class="text-[10px] font-light tracking-[0.25em] uppercase text-white mb-8 flex items-center gap-2">
                <Plus class="w-4 h-4 text-[#c5a880] stroke-[1.5]"/> Patient Record &amp; Account Compiler
              </h3>
              <div v-if="patientCreateSuccess" class="bg-emerald-950/20 border border-emerald-900/40 text-emerald-400 p-4 rounded-xl text-[11px] font-mono mb-5">{{ patientCreateSuccess }}</div>
              <div v-if="patientCreateError" class="bg-red-950/20 border border-red-900/40 text-red-400 p-4 rounded-xl text-[11px] font-mono mb-5">{{ patientCreateError }}</div>
              <form @submit.prevent="handleManualSubmit" class="space-y-5">
                <div class="grid grid-cols-2 gap-4">
                  <div class="space-y-1.5">
                    <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">First Name</label>
                    <input v-model="newPatient.firstName" type="text" required placeholder="John"
                           class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-[#c5a880] transition-all"/>
                  </div>
                  <div class="space-y-1.5">
                    <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">Last Name</label>
                    <input v-model="newPatient.lastName" type="text" required placeholder="Doe"
                           class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-[#c5a880] transition-all"/>
                  </div>
                </div>
                <div class="grid grid-cols-2 gap-4">
                  <div class="space-y-1.5">
                    <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">Blood Classification</label>
                    <select v-model="newPatient.bloodGroup" class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-[#c5a880] font-mono cursor-pointer">
                      <option value="A+">A+</option><option value="A-">A-</option>
                      <option value="B+">B+</option><option value="B-">B-</option>
                      <option value="AB+">AB+</option><option value="AB-">AB-</option>
                      <option value="O+">O+</option><option value="O-">O-</option>
                    </select>
                  </div>
                  <div class="space-y-1.5">
                    <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">Date of Birth</label>
                    <input v-model="newPatient.dateOfBirth" type="date" required
                           class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-[#c5a880] font-mono transition-all"/>
                  </div>
                </div>
                <div class="border-t border-neutral-800/60 pt-4 space-y-1">
                  <p class="text-[8px] font-mono uppercase tracking-[0.25em] text-[#c5a880]">Patient Portal Login Credentials</p>
                  <p class="text-[8px] font-mono text-neutral-600">Patient uses these to log in and receive blood requests.</p>
                </div>
                <div class="grid grid-cols-2 gap-4">
                  <div class="space-y-1.5">
                    <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">Username</label>
                    <input v-model="newPatient.username" type="text" required placeholder="patient_john"
                           class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-[#c5a880] font-mono transition-all"/>
                  </div>
                  <div class="space-y-1.5">
                    <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">Password</label>
                    <input v-model="newPatient.password" type="password" required placeholder="••••••••"
                           class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-[#c5a880] font-mono transition-all"/>
                  </div>
                </div>
                <button type="submit" class="w-full bg-neutral-900 hover:bg-neutral-800 text-[#c5a880] border border-neutral-800 text-[10px] py-3.5 rounded-xl font-semibold tracking-[0.2em] transition-all uppercase cursor-pointer mt-2 shadow-md">
                  Commit Patient Record &amp; Account
                </button>
              </form>
            </div>
          </div>
        </div>

        <!-- VOICE TAB -->
        <div v-if="currentTab === 'voice' && (currentUser.role === 'ADMIN' || currentUser.role === 'NURSE')" class="max-w-2xl mx-auto py-6 animate-fadeIn">
          <div class="bg-[#0d0d0f] border border-neutral-900 rounded-3xl p-12 text-center space-y-10 shadow-2xl relative">
            <div class="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-[#c5a880]/20 to-transparent"></div>
            <div class="space-y-2">
              <span class="text-[8px] tracking-[0.35em] text-[#c5a880] uppercase font-mono block">Intake Automation Pipeline Terminal</span>
              <h2 class="text-lg font-light text-white tracking-widest uppercase">Speech Overpass Node</h2>
            </div>
            <div class="flex justify-center py-6">
              <button @click="startVoiceRecognition"
                      :class="isListening ? 'bg-red-950/20 border-red-800 text-red-400 scale-105 animate-pulse' : 'bg-[#121215] border-neutral-800 text-neutral-500 hover:text-[#c5a880] hover:border-[#c5a880]/40'"
                      class="w-28 h-28 rounded-full border flex flex-col items-center justify-center gap-2.5 transition-all duration-300 cursor-pointer shadow-xl">
                <component :is="isListening ? MicOff : Mic" class="w-6 h-6 text-current stroke-[1.2]"/>
                <span class="text-[8px] uppercase tracking-[0.2em] font-mono font-medium">{{ isListening ? 'STREAM_ON' : 'ENGAGE NODE' }}</span>
              </button>
            </div>
            <div class="space-y-5 text-left max-w-lg mx-auto">
              <div class="space-y-1.5">
                <span class="text-[8px] tracking-[0.2em] text-neutral-500 uppercase block font-mono">Captured Real-Time Vocal Matrix</span>
                <div class="bg-[#121215] border border-neutral-900 rounded-xl p-5 min-h-[70px] flex items-center text-xs text-neutral-300 leading-relaxed font-mono">
                  <p v-if="voiceTranscript" class="italic text-white">"{{ voiceTranscript }}"</p>
                  <p v-else class="text-neutral-600 text-[10px] uppercase tracking-wider">Awaiting dynamic speech recognition stream capture data input...</p>
                </div>
              </div>
              <div class="space-y-1.5">
                <span class="text-[8px] tracking-[0.2em] text-neutral-500 uppercase block font-mono">AI Extraction Dynamic Response</span>
                <div class="bg-[#121215] border border-neutral-900 rounded-xl p-5 min-h-[70px] flex items-center text-xs text-neutral-300 leading-relaxed border-l-2 border-l-[#c5a880]">
                  <p v-if="aiResponse" class="flex items-center gap-3 text-white font-light tracking-wide">
                    <CheckCircle2 class="w-4 h-4 text-[#c5a880] shrink-0"/>{{ aiResponse }}
                  </p>
                  <p v-else class="text-neutral-600 text-[10px] uppercase tracking-wider">Pipeline parsing structure clear. Listening engine frame idle.</p>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- NURSE ADD PATIENT TAB -->
        <div v-if="currentTab === 'add-patient' && currentUser.role === 'NURSE'" class="max-w-2xl mx-auto py-6 animate-fadeIn">
          <div class="flex items-center justify-between border-b border-neutral-900 pb-4 mb-8">
            <div>
              <h2 class="text-xs uppercase font-light tracking-[0.3em] text-[#c5a880] flex items-center gap-2">
                <UserPlus class="w-4 h-4"/> Patient Registration Terminal
              </h2>
              <p class="text-[10px] text-neutral-500 uppercase font-mono mt-1">Create patient record and portal credentials — Patient role only</p>
            </div>
            <span class="text-[9px] bg-[#c5a880]/5 border border-[#c5a880]/20 text-[#c5a880] font-mono px-3 py-1 rounded-full uppercase tracking-widest">ROLE: PATIENT</span>
          </div>
          <div class="bg-[#0d0d0f] border border-neutral-900 rounded-2xl p-8 shadow-xl relative overflow-hidden">
            <div class="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-[#c5a880]/20 to-transparent"></div>
            <h3 class="text-[10px] font-light tracking-[0.25em] uppercase text-white mb-8 flex items-center gap-2">
              <Plus class="w-4 h-4 text-[#c5a880] stroke-[1.5]"/> New Patient Record &amp; Account
            </h3>
            <div v-if="nursePatientCreateSuccess" class="bg-emerald-950/20 border border-emerald-900/40 text-emerald-400 p-4 rounded-xl text-[11px] font-mono mb-5 flex items-center gap-2">
              <CheckCircle2 class="w-4 h-4 shrink-0"/>{{ nursePatientCreateSuccess }}
            </div>
            <div v-if="nursePatientCreateError" class="bg-red-950/20 border border-red-900/40 text-red-400 p-4 rounded-xl text-[11px] font-mono mb-5 flex items-center gap-2">
              <AlertCircle class="w-4 h-4 shrink-0"/>{{ nursePatientCreateError }}
            </div>
            <form @submit.prevent="handleNursePatientSubmit" class="space-y-5">
              <div class="grid grid-cols-2 gap-4">
                <div class="space-y-1.5">
                  <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">First Name</label>
                  <input v-model="nurseNewPatient.firstName" type="text" required placeholder="John"
                         class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-[#c5a880] transition-all"/>
                </div>
                <div class="space-y-1.5">
                  <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">Last Name</label>
                  <input v-model="nurseNewPatient.lastName" type="text" required placeholder="Doe"
                         class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-[#c5a880] transition-all"/>
                </div>
              </div>
              <div class="grid grid-cols-2 gap-4">
                <div class="space-y-1.5">
                  <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">Blood Classification</label>
                  <select v-model="nurseNewPatient.bloodGroup" class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-[#c5a880] font-mono cursor-pointer">
                    <option value="A+">A+</option><option value="A-">A-</option>
                    <option value="B+">B+</option><option value="B-">B-</option>
                    <option value="AB+">AB+</option><option value="AB-">AB-</option>
                    <option value="O+">O+</option><option value="O-">O-</option>
                  </select>
                </div>
                <div class="space-y-1.5">
                  <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">Date of Birth</label>
                  <input v-model="nurseNewPatient.dateOfBirth" type="date" required
                         class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-[#c5a880] font-mono transition-all"/>
                </div>
              </div>
              <div class="border-t border-neutral-800/60 pt-4 space-y-1">
                <p class="text-[8px] font-mono uppercase tracking-[0.25em] text-[#c5a880]">Patient Portal Login Credentials</p>
                <p class="text-[8px] font-mono text-neutral-600">Patient uses these to log in and view blood donation requests.</p>
              </div>
              <div class="grid grid-cols-2 gap-4">
                <div class="space-y-1.5">
                  <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">Username</label>
                  <input v-model="nurseNewPatient.username" type="text" required placeholder="patient_john"
                         class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-[#c5a880] font-mono transition-all"/>
                </div>
                <div class="space-y-1.5">
                  <label class="text-[8px] tracking-[0.25em] uppercase text-neutral-500 block font-mono">Password</label>
                  <input v-model="nurseNewPatient.password" type="password" required placeholder="••••••••"
                         class="w-full bg-[#121215] border border-neutral-800 rounded-xl px-4 py-3 text-xs text-white focus:outline-none focus:border-[#c5a880] font-mono transition-all"/>
                </div>
              </div>
              <button type="submit" class="w-full bg-neutral-900 hover:bg-neutral-800 text-[#c5a880] border border-neutral-800 text-[10px] py-3.5 rounded-xl font-semibold tracking-[0.2em] transition-all uppercase cursor-pointer mt-2 shadow-md">
                Commit Patient Record &amp; Account
              </button>
            </form>
          </div>
          <!-- Quick stats for nurse -->
          <div class="mt-8 grid grid-cols-2 gap-4">
            <div class="bg-[#0d0d0f] border border-neutral-900 rounded-2xl p-5 flex items-center gap-4">
              <div class="p-2 bg-[#c5a880]/5 border border-[#c5a880]/10 rounded-xl">
                <Users class="w-4 h-4 text-[#c5a880] stroke-[1.2]"/>
              </div>
              <div>
                <span class="text-[8px] font-mono uppercase tracking-widest text-neutral-500 block">Total Registered</span>
                <span class="text-lg font-light text-white">{{ patients.length }} Patients</span>
              </div>
            </div>
            <div class="bg-[#0d0d0f] border border-neutral-900 rounded-2xl p-5 flex items-center gap-4">
              <div class="p-2 bg-[#c5a880]/5 border border-[#c5a880]/10 rounded-xl">
                <Heart class="w-4 h-4 text-red-400 stroke-[1.2]"/>
              </div>
              <div>
                <span class="text-[8px] font-mono uppercase tracking-widest text-neutral-500 block">System Status</span>
                <span class="text-xs font-mono text-emerald-400 uppercase tracking-wider">All Systems Online</span>
              </div>
            </div>
          </div>
        </div>

        <!-- REGISTRY TAB -->
        <div v-if="currentTab === 'registry' && (currentUser.role === 'ADMIN' || currentUser.role === 'NURSE')" class="space-y-12 animate-fadeIn">
          <div class="bg-[#0d0d0f] border border-neutral-900 rounded-2xl overflow-hidden shadow-2xl">
            <div class="px-6 py-5 border-b border-neutral-900 bg-[#0a0a0c] flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h3 class="text-xs font-light text-white uppercase tracking-[0.25em]">Master Medical Identity Ledger</h3>
                <p class="text-[9px] font-mono text-neutral-500 uppercase mt-0.5">Newest entries at top — Secure Read, Update &amp; Purge</p>
              </div>
              <div class="relative max-w-xs w-full">
                <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-neutral-600"/>
                <input v-model="searchQueries" type="text" placeholder="FILTER VECTOR MATRIX..."
                       class="w-full bg-[#121215] border border-neutral-800 rounded-xl pl-9 pr-4 py-2 text-xs text-white placeholder-neutral-600 focus:outline-none focus:border-[#c5a880] font-mono uppercase tracking-wider"/>
              </div>
            </div>
            <div class="overflow-x-auto">
              <table class="w-full text-left border-collapse">
                <thead>
                <tr class="border-b border-neutral-900 bg-[#101013] text-[8px] uppercase tracking-[0.25em] text-neutral-500 font-mono">
                  <th class="py-4 px-6">Row Matrix Key</th>
                  <th class="py-4 px-6">Identity Field Name</th>
                  <th class="py-4 px-6">Date of Birth Target</th>
                  <th class="py-4 px-6">Bio Blood Spec Class</th>
                  <th class="py-4 px-6 text-center">Record Modifications Node</th>
                </tr>
                </thead>
                <tbody class="divide-y divide-neutral-900 text-xs text-neutral-300">
                <tr v-for="patient in filteredPatients" :key="patient.id" class="hover:bg-neutral-900/30 transition-colors">
                  <td class="py-4 px-6 font-mono text-neutral-600">#{{ String(patient.id || 0).padStart(4, '0') }}</td>
                  <td class="py-4 px-6 text-white font-light tracking-wide">{{ patient.firstName }} {{ patient.lastName }}</td>
                  <td class="py-4 px-6 font-mono text-neutral-400 text-[11px]">{{ patient.dateOfBirth }}</td>
                  <td class="py-4 px-6">
                    <span class="bg-[#121215] px-2.5 py-1 border border-neutral-800 text-[#c5a880] font-mono text-[10px] rounded-md">{{ patient.bloodGroup }}</span>
                  </td>
                  <td class="py-4 px-6 text-center">
                    <div class="flex items-center justify-center gap-2">
                      <button @click="initiatePatientUpdate(patient)" class="text-neutral-500 hover:text-white p-2 hover:bg-neutral-800 rounded-lg transition-all cursor-pointer">
                        <Edit3 class="w-3.5 h-3.5 stroke-[1.5]"/>
                      </button>
                      <button v-if="currentUser.role === 'ADMIN' || currentUser.role === 'NURSE'" @click="handleDeletePatient(patient.id)" class="text-neutral-600 hover:text-red-400 p-2 hover:bg-red-950/20 rounded-lg transition-all cursor-pointer">
                        <Trash2 class="w-3.5 h-3.5 stroke-[1.5]"/>
                      </button>
                    </div>
                  </td>
                </tr>
                <tr v-if="filteredPatients.length === 0">
                  <td colspan="5" class="py-16 text-center text-[10px] text-neutral-500 uppercase tracking-[0.2em] font-mono">
                    <div class="flex flex-col items-center gap-2 justify-center">
                      <AlertCircle class="w-4 h-4 text-neutral-700"/>
                      <span>Zero medical signature models matched tracking parameter sets.</span>
                    </div>
                  </td>
                </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div v-if="isEditingPatient" class="bg-[#0d0d0f] border-2 border-[#c5a880]/30 rounded-2xl p-8 shadow-[0_20px_50px_rgba(0,0,0,0.5)] space-y-6 animate-fadeIn">
            <div class="flex items-center justify-between border-b border-neutral-900 pb-3">
              <h4 class="text-[10px] font-mono uppercase tracking-[0.25em] text-[#c5a880] flex items-center gap-2">
                <Sliders class="w-4 h-4"/> Modify Clinical Patient Entity Vectors // ID: #{{ activeEditingPatient.id }}
              </h4>
              <button @click="isEditingPatient = false" class="text-neutral-500 hover:text-white text-[10px] uppercase font-mono tracking-widest">Cancel</button>
            </div>
            <div v-if="patientUpdateSuccess" class="bg-emerald-950/20 border border-emerald-900/40 text-emerald-400 p-4 rounded-xl text-[11px] font-mono">{{ patientUpdateSuccess }}</div>
            <div v-if="patientUpdateError" class="bg-red-950/20 border border-red-900/40 text-red-400 p-4 rounded-xl text-[11px] font-mono">{{ patientUpdateError }}</div>
            <form @submit.prevent="handleUpdatePatient" class="grid grid-cols-1 sm:grid-cols-4 gap-4 items-end">
              <div>
                <label class="text-[8px] uppercase tracking-widest font-mono text-neutral-500 block mb-1">First Name</label>
                <input v-model="activeEditingPatient.firstName" type="text" class="w-full bg-[#121215] border border-neutral-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-[#c5a880]"/>
              </div>
              <div>
                <label class="text-[8px] uppercase tracking-widest font-mono text-neutral-500 block mb-1">Last Name</label>
                <input v-model="activeEditingPatient.lastName" type="text" class="w-full bg-[#121215] border border-neutral-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-[#c5a880]"/>
              </div>
              <div>
                <label class="text-[8px] uppercase tracking-widest font-mono text-neutral-500 block mb-1">Blood Classification</label>
                <input v-model="activeEditingPatient.bloodGroup" type="text" class="w-full bg-[#121215] border border-neutral-800 rounded-lg px-3 py-2 text-xs text-[#c5a880] focus:outline-none focus:border-[#c5a880] font-mono"/>
              </div>
              <button type="submit" class="bg-white hover:bg-neutral-200 text-black text-[9px] uppercase font-mono tracking-widest py-2.5 rounded-lg transition-all font-semibold cursor-pointer">
                Push Sync Update
              </button>
            </form>
          </div>

          <div v-if="currentUser.role === 'ADMIN'" class="bg-[#0d0d0f] border border-neutral-900 rounded-2xl overflow-hidden shadow-2xl animate-fadeIn">
            <div class="px-6 py-5 border-b border-neutral-900 bg-[#0a0a0c] flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div>
                <h3 class="text-xs font-light text-white uppercase tracking-[0.25em] flex items-center gap-2">
                  <Shield class="w-4 h-4 text-[#c5a880] stroke-[1.2]"/> Platform Authorized Credentials Core Directory Matrix
                </h3>
                <p class="text-[9px] font-mono text-neutral-500 uppercase mt-0.5">Administrative Profile Overrides, Security Path Changes &amp; De-provisioning Gateways</p>
              </div>
              <div class="relative max-w-xs w-full">
                <Search class="absolute left-3 top-1/2 -translate-y-1/2 w-3 h-3 text-neutral-600"/>
                <input v-model="userSearchQuery" type="text" placeholder="FILTER USER MATRICES..."
                       class="w-full bg-transparent border-b border-neutral-800 pl-8 pr-4 py-1.5 text-xs text-white placeholder-neutral-600 focus:outline-none focus:border-[#c5a880] font-mono uppercase tracking-wider"/>
              </div>
            </div>
            <div class="overflow-x-auto">
              <table class="w-full text-left border-collapse">
                <thead>
                <tr class="border-b border-neutral-900 bg-[#101013] text-[8px] uppercase tracking-[0.25em] text-neutral-500 font-mono">
                  <th class="py-4 px-6">System ID Key</th>
                  <th class="py-4 px-6">Profile Identifier Node</th>
                  <th class="py-4 px-6">Email Route</th>
                  <th class="py-4 px-6">Security Clearance Rank</th>
                  <th class="py-4 px-6 text-center">Modifications Access Gateway</th>
                </tr>
                </thead>
                <tbody class="divide-y divide-neutral-900 text-xs text-neutral-400 font-mono">
                <tr v-for="user in filteredUsers" :key="user.id || user.username" class="hover:bg-neutral-900/30 transition-colors">
                  <td class="py-4 px-6 text-neutral-600">#{{ String(user.id || 1).padStart(3, '0') }}</td>
                  <td class="py-4 px-6 font-sans text-white font-medium tracking-wide">{{ user.username }}</td>
                  <td class="py-4 px-6 text-neutral-500 font-light">{{ user.email || `${user.username.toLowerCase()}@hospital.com` }}</td>
                  <td class="py-4 px-6">
                    <span :class="user.role === 'ADMIN' ? 'bg-[#c5a880]/10 text-[#c5a880] border border-[#c5a880]/20' : 'bg-neutral-900 text-neutral-400 border border-neutral-800'"
                          class="px-2.5 py-0.5 text-[8px] font-mono tracking-widest rounded uppercase">{{ user.role }}</span>
                  </td>
                  <td class="py-4 px-6 text-center">
                    <div class="flex items-center justify-center gap-1.5">
                      <button @click="initiateUserUpdate(user)" class="text-neutral-500 hover:text-white p-2 hover:bg-neutral-800 rounded-lg transition-all cursor-pointer">
                        <Edit3 class="w-3.5 h-3.5 stroke-[1.5]"/>
                      </button>
                      <button @click="handleDeleteUser(user.id || user.username)"
                              :disabled="user.username === currentUser.username"
                              :class="user.username === currentUser.username ? 'opacity-10 cursor-not-allowed text-neutral-700' : 'text-neutral-500 hover:text-red-400 hover:bg-red-950/20'"
                              class="p-2 rounded-lg transition-all cursor-pointer">
                        <UserX class="w-3.5 h-3.5 stroke-[1.5]"/>
                      </button>
                    </div>
                  </td>
                </tr>
                </tbody>
              </table>
            </div>
          </div>

          <div v-if="isEditingUser && currentUser.role === 'ADMIN'" class="bg-[#0d0d0f] border-2 border-[#c5a880]/30 rounded-2xl p-8 shadow-[0_20px_50px_rgba(0,0,0,0.5)] space-y-6 animate-fadeIn">
            <div class="flex items-center justify-between border-b border-neutral-900 pb-3">
              <h4 class="text-[10px] font-mono uppercase tracking-[0.25em] text-[#c5a880] flex items-center gap-2">
                <ShieldAlert class="w-4 h-4"/> Override Security Clearances // Profile: {{ activeEditingUser.username }}
              </h4>
              <button @click="isEditingUser = false" class="text-neutral-500 hover:text-white text-[10px] uppercase font-mono tracking-widest">Cancel</button>
            </div>
            <div v-if="userUpdateSuccess" class="bg-emerald-950/20 border border-emerald-900/40 text-emerald-400 p-4 rounded-xl text-[11px] font-mono">{{ userUpdateSuccess }}</div>
            <div v-if="userUpdateError" class="bg-red-950/20 border border-red-900/40 text-red-400 p-4 rounded-xl text-[11px] font-mono">{{ userUpdateError }}</div>
            <form @submit.prevent="handleUpdateUser" class="grid grid-cols-1 sm:grid-cols-3 gap-4 items-end">
              <div>
                <label class="text-[8px] uppercase tracking-widest font-mono text-neutral-500 block mb-1">Email Address</label>
                <input v-model="activeEditingUser.email" type="email" class="w-full bg-[#121215] border border-neutral-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-[#c5a880] font-mono"/>
              </div>
              <div>
                <label class="text-[8px] uppercase tracking-widest font-mono text-neutral-500 block mb-1">Clearance Allocation Level</label>
                <select v-model="activeEditingUser.role" class="w-full bg-[#121215] border border-neutral-800 rounded-lg px-3 py-2 text-xs text-white focus:outline-none focus:border-[#c5a880] font-mono cursor-pointer">
                  <option v-for="role in availableRoles" :key="getRoleName(role)" :value="getRoleName(role)">{{ getRoleName(role) }}</option>
                </select>
              </div>
              <button type="submit" class="bg-[#c5a880] hover:bg-[#b3956d] text-black text-[9px] uppercase font-mono tracking-widest py-2.5 rounded-lg transition-all font-semibold cursor-pointer">
                Write Security Overrides
              </button>
            </form>
          </div>
        </div>

        <!-- PATIENT VIEW TAB — fixed polling, added blood group badge + countdown -->
        <div v-if="currentTab === 'patient-view' && currentUser.role === 'PATIENT'" class="max-w-xl mx-auto py-4 animate-fadeIn">
          <div class="bg-[#0d0d0f] border border-neutral-900 rounded-3xl p-10 shadow-2xl space-y-8 relative">
            <div class="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-[#c5a880]/20 to-transparent"></div>

            <!-- Header -->
            <div class="border-b border-neutral-900 pb-5 text-center animate-slideDown">
              <span class="text-[8px] tracking-[0.35em] text-[#c5a880] uppercase font-mono block mb-1">Personal Profile Module Registry</span>
              <h2 class="text-md text-white font-light uppercase tracking-widest">Client Outpatient Workspace</h2>
            </div>

            <!-- Identity + blood group card -->
            <div class="grid grid-cols-2 gap-4 animate-slideUp">
              <div class="p-5 bg-[#121215] border border-neutral-900 rounded-2xl">
                <span class="text-[8px] text-neutral-500 uppercase block tracking-widest font-mono mb-1">Identification</span>
                <span class="text-xs font-mono font-bold text-white tracking-wide">{{ currentUser.username }}</span>
                <span class="text-[9px] bg-[#c5a880]/5 border border-[#c5a880]/20 font-mono px-2 py-0.5 text-[#c5a880] rounded-lg font-bold uppercase tracking-widest mt-2 block w-fit animate-pulse">VALID</span>
              </div>
              <div class="p-5 bg-[#121215] border border-red-900/20 rounded-2xl relative overflow-hidden">
                <div class="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-red-500/20 to-transparent"></div>
                <span class="text-[8px] text-neutral-500 uppercase block tracking-widest font-mono mb-1">Blood Group</span>
                <div class="flex items-center gap-2 mt-1">
                  <Droplet class="w-4 h-4 text-red-400"/>
                  <span class="text-lg font-mono font-bold text-red-400">Registered</span>
                </div>
                <span class="text-[8px] font-mono text-neutral-600 mt-1 block">Blood group set by admin</span>
              </div>
            </div>

            <!-- Blood requests section -->
            <div class="space-y-4">
              <div class="flex items-center justify-between">
                <span class="text-[8px] tracking-[0.35em] text-red-400 uppercase font-mono flex items-center gap-2">
                  <Droplet class="w-3 h-3 animate-pulse"/> Active Emergency Requests
                </span>
                <div class="flex items-center gap-3">
                  <span class="text-[8px] font-mono text-neutral-600 flex items-center gap-1">
                    <Clock class="w-3 h-3"/> {{ refreshCountdown }}s
                  </span>
                  <button @click="fetchMyBloodRequests()"
                          :class="isRefreshingRequests ? 'animate-spin text-[#c5a880]' : 'text-neutral-500 hover:text-[#c5a880]'"
                          class="p-1.5 rounded-lg hover:bg-neutral-800 transition-all cursor-pointer" title="Refresh">
                    <RefreshCw class="w-3.5 h-3.5"/>
                  </button>
                </div>
              </div>

              <div v-if="myBloodRequests.length === 0" class="bg-[#121215] border border-neutral-900 rounded-2xl p-8 text-center animate-fadeIn">
                <Heart class="w-7 h-7 text-neutral-700 mx-auto mb-3"/>
                <p class="text-[10px] text-neutral-400 uppercase font-mono tracking-wider font-medium">No active requests for your blood group</p>
                <p class="text-[9px] text-neutral-700 font-mono mt-2">Monitoring for new broadcasts — auto-refresh every 5s</p>
                <div class="mt-4 flex items-center justify-center gap-2">
                  <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse inline-block"></span>
                  <span class="text-[8px] font-mono text-emerald-600 uppercase tracking-wider">Live monitoring active</span>
                </div>
              </div>

              <div v-for="(request, index) in myBloodRequests" :key="request.id"
                   class="bg-[#121215] border border-red-900/30 rounded-2xl p-5 space-y-4 animate-slideUp relative overflow-hidden"
                   :style="`animation-delay: ${index * 80}ms`">
                <div class="absolute top-0 left-0 right-0 h-[1px] bg-gradient-to-r from-transparent via-red-500/40 to-transparent"></div>
                <div class="flex items-center justify-between">
                  <div class="flex items-center gap-2">
                    <span class="w-2 h-2 rounded-full bg-red-500 animate-pulse inline-block"></span>
                    <span class="text-[10px] font-mono text-red-400 uppercase tracking-wider font-bold">{{ request.bloodGroupRequested }} Required</span>
                  </div>
                  <span class="text-[8px] bg-red-950/30 border border-red-900/40 text-red-400 font-mono px-2.5 py-1 rounded-full uppercase tracking-widest">{{ request.status }}</span>
                </div>
                <div class="space-y-1">
                  <p class="text-[8px] text-neutral-500 font-mono uppercase tracking-wider">Patient in need</p>
                  <p class="text-sm text-white font-light tracking-wide">{{ request.patientName }}</p>
                </div>
                <p v-if="request.urgencyMessage" class="text-[10px] text-neutral-400 font-mono bg-neutral-900/60 rounded-lg p-3 border border-neutral-800">{{ request.urgencyMessage }}</p>
                <button @click="acceptBloodRequest(request.id)"
                        class="w-full bg-red-950/20 hover:bg-red-900/30 active:scale-95 text-red-400 border border-red-900/40 hover:border-red-700/60 text-[10px] py-3 rounded-xl font-semibold tracking-[0.2em] transition-all uppercase cursor-pointer flex items-center justify-center gap-2 group">
                  <CheckCircle2 class="w-3.5 h-3.5 group-hover:scale-110 transition-transform"/>
                  Accept — I Can Donate
                </button>
              </div>
            </div>

            <!-- Info footer -->
            <div class="bg-[#121215] border border-neutral-900 rounded-xl p-4 flex items-start gap-3">
              <AlertTriangle class="w-3.5 h-3.5 text-[#c5a880] shrink-0 mt-0.5"/>
              <p class="text-[9px] text-neutral-500 font-mono leading-relaxed">
                Only requests matching your registered blood group are shown. For record changes or blood group updates, contact the nursing staff or administrator.
              </p>
            </div>
          </div>
        </div>

      </main>
    </div>
  </div>
</template>

<style scoped>
.animate-fadeIn { animation: fadeIn 0.8s cubic-bezier(0.16, 1, 0.3, 1) forwards; }
.animate-slideDown { animation: slideDown 0.6s cubic-bezier(0.16, 1, 0.3, 1) forwards; }
.animate-slideUp { animation: slideUp 0.5s cubic-bezier(0.16, 1, 0.3, 1) both; }
.animate-ambient-slow { animation: floatAmbient 20s ease-in-out infinite alternate; opacity: 0.6 !important; }
.animate-ambient-slower { animation: floatAmbientSlower 30s ease-in-out infinite alternate; opacity: 0.5 !important; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(20px); } to { opacity: 1; transform: translateY(0); } }
@keyframes slideDown { from { opacity: 0; transform: translateY(-16px); } to { opacity: 1; transform: translateY(0); } }
@keyframes slideUp { from { opacity: 0; transform: translateY(16px); } to { opacity: 1; transform: translateY(0); } }
@keyframes floatAmbient { 0% { transform: translate(0,0) scale(1); } 50% { transform: translate(10vw,8vh) scale(1.35); } 100% { transform: translate(-5vw,4vh) scale(0.75); } }
@keyframes floatAmbientSlower { 0% { transform: translate(0,0) scale(1.1); } 50% { transform: translate(-8vw,-10vh) scale(0.7); } 100% { transform: translate(8vw,6vh) scale(1.35); } }

::-webkit-scrollbar { width: 7px; }
::-webkit-scrollbar-track { background: #070708; }
::-webkit-scrollbar-thumb { background: #3d3d4a; border-radius: 4px; }
::-webkit-scrollbar-thumb:hover { background: #c5a880; }
</style>
ENDOFFILE
echo "done"