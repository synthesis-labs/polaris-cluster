import React, { Component } from 'react';
import './App.css';

import { Provider } from 'react-redux';
import { createStore, applyMiddleware } from 'redux';
import { combineReducers } from 'redux'
import thunk from 'redux-thunk';

import List from './List'

const list_reducer = (state = [], action) => {
  console.log(`list_reducer current: ${JSON.stringify(state)} action: ${JSON.stringify(action)}`)
  if (action.type != "LIST")
    return state
  else {
    switch (action.action) {
      case "ACTIVE": {
        return state
            .filter((l) => { return l.name != action.data.name })
            .concat(action.data)
        break
      }
      case "DELETED": {
        return state
            .filter((l) => { return l.name != action.data.name })
        break
      }
      default:
        console.log("Some other state not being handled?")
        return state
    }
  }
}

const root_reducer = combineReducers({
  lists: list_reducer,
})

class App extends Component {

  constructor(props) {
    super(props);
    this.state = {
      store: createStore(
        root_reducer,
        applyMiddleware(thunk),
      ),
      new_list_name: "",
    };
    console.log(this.state.store)

    this.new_list = this.new_list.bind(this)
    this.handle_new_list_name_change = this.handle_new_list_name_change.bind(this)
    this.refresh = this.refresh.bind(this)
    this.spam = this.spam.bind(this)

    this.connect()
  }

  connect() {
    this.ws = new WebSocket('ws://localhost:8080/updates');
    this.ws.onmessage = evt => {
      // When we receive an update from the ws, we want to dispatch this
      // to the state store
      //
      const updates = JSON.parse(evt.data)
      if (updates instanceof Array) {
        // A list of events to dispatch
        //
        updates.map((update) => {
          this.state.store.dispatch(update)
        })
      }
      else {
        // A single event
        //
        this.state.store.dispatch(updates)
      }
    }

    this.ws.onclose =
      (err) => {
        console.log(`Socket closed. Reconnect in 5 seconds. (${err.message})`)
        setTimeout(() => {
        this.connect()
      },
      5000)
    }

    this.ws.onerror = (err) => {
      console.error(`Socket error, closed. (${err.message})`)
      this.ws.close()
    }

    // Get the list as we connect
    //
    this.ws.onopen = () => {
      this.refresh()
    }
  }

  componentWillMount() {
    // Notify react to update when the state store updates
    //
    this.state.store.subscribe(() => {
      this.forceUpdate();
    });
  }

  handle_new_list_name_change(event) {
    this.setState({new_list_name: event.target.value})
  }

  new_list() {
    const cmd = {
      type: "LIST",
      cmd: "CREATE",
      data: {
        name: this.state.new_list_name
      }
    };

    this.ws.send(JSON.stringify(cmd));
  }

  spam(f) {
    setTimeout(() => {
      for (let i = 0; i < 9999; ++i)
        f()
    }, 500)
  }

  refresh() {
    const cmd = {
      type: "REFRESH"
    }

    this.ws.send(JSON.stringify(cmd));
  }

  render() {
    return (
      <Provider store={this.state.store}>
        <div className="App">
          <header className="App-header">
            <div>
              {this.state.store.getState().lists.map((list) => <List key={list.name} {...list} ws={this.ws}></List>)}
            </div>
            <input type="text"
              value={this.state.new_list_name}
              onChange={this.handle_new_list_name_change}
            />
            <button onClick={ this.new_list }>Create list</button>
            <button onClick={ this.refresh }>Refresh</button>
            <button onClick={ () => this.spam(this.new_list) }>Spam Create list</button>
            <button onClick={ () => this.spam(this.refresh)  }>Spam Refresh</button>
            <p>
              { JSON.stringify(this.state.store.getState())}
            </p>
          </header>
        </div>
      </Provider>
    );
  }
}

export default App;
