import { createI18n } from 'vue-i18n'
import { API_URL } from './config'

export async function loadLanguage() {
    const response = await fetch(`${API_URL}translation`).catch(function () {
        return new Response(JSON.stringify({
            code: 400,
            message: '{}'
        }))
    })
    return await response.json()
}

export function initI18n() {

    return loadLanguage().then(function (data) {
        let i18n = createI18n({
            locale: 'default',
            fallbackLocale: 'default',
            messages: data
        })
        i18n.global.setLocaleMessage("default", data)
        return i18n
    })
}