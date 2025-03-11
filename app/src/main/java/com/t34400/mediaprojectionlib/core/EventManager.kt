package com.t34400.mediaprojectionlib.core

interface IEventListener<T> {
    fun onEvent(data: T)
}

class EventManager<T> {
    private val listeners = mutableListOf<IEventListener<T>>()

    fun addListener(listener: IEventListener<T>) {
        synchronized(this) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: IEventListener<T>) {
        synchronized(this) {
            listeners.remove(listener)
        }
    }

    fun notifyListeners(data: T) {
        synchronized(this) {
            for (listener in listeners) {
                listener.onEvent(data)
            }
        }
    }
}