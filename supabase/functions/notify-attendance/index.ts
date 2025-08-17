// Supabase Edge Function: notify-attendance
// Save as supabase/functions/notify-attendance/index.ts

import { serve } from 'https://deno.land/std@0.177.0/http/server.ts'

const ONESIGNAL_APP_ID = '5707627c-23d3-41da-8d32-309113db8718'
const ONESIGNAL_REST_API_KEY = 'os_v2_app_k4dwe7bd2na5vdjsgcirhw4hdcl4kaojncseofffamdip4qnke6ptolxwisrlfkiyueihhtokj6e5ar5ztnvzxebxjuvja3pbxdk7cy'

interface NotificationRequest {
session_code: string
roll_numbers: string[]
teacher_id: string
completed_at: string
}

serve(async (req) => {
  try {
    const { session_code, roll_numbers, teacher_id, completed_at }: NotificationRequest = await req.json()

    if (!roll_numbers || roll_numbers.length === 0) {
      return new Response(
        JSON.stringify({ error: 'No roll numbers provided' }),
        { status: 400, headers: { 'Content-Type': 'application/json' } }
)
}

// Prepare OneSignal notification payload
const notificationPayload = {
app_id: ONESIGNAL_APP_ID,
include_external_user_ids: roll_numbers, // Roll numbers as external user IDs
headings: {
"en": "Attendance Confirmed ✅"
},
contents: {
"en": "Your attendance has been successfully recorded"
},
data: {
type: "attendance_confirmation",
session_code: session_code,
confirmed_at: completed_at
},
// Personalize message with roll number
template_id: undefined // Use custom content
}

// Send to OneSignal
const oneSignalResponse = await fetch('https://onesignal.com/api/v1/notifications', {
method: 'POST',
headers: {
'Content-Type': 'application/json',
'Authorization': `Basic ${ONESIGNAL_REST_API_KEY}`
},
body: JSON.stringify(notificationPayload)
    })

    const oneSignalResult = await oneSignalResponse.json()

    if (oneSignalResponse.ok) {
      console.log(`Successfully sent notifications to ${roll_numbers.length} students for session ${session_code}`)

      return new Response(
        JSON.stringify({
          success: true,
          session_code,
          notifications_sent: roll_numbers.length,
          onesignal_response: oneSignalResult
        }),
        {
          status: 200,
          headers: { 'Content-Type': 'application/json' }
        }
)
} else {
console.error('OneSignal error:', oneSignalResult)

      return new Response(
        JSON.stringify({
          error: 'Failed to send notifications',
          onesignal_error: oneSignalResult
        }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
)
}

} catch (error) {
console.error('Edge function error:', error)

    return new Response(
      JSON.stringify({
        error: 'Internal server error',
        message: error.message
      }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
)
}
})