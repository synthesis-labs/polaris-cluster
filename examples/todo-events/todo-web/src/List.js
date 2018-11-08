import React, { Component } from 'react';
import PropTypes from 'prop-types';

class List extends Component {
  constructor(props) {
    super(props);
    this.state = {
      name: props.name,
      ws: props.ws
    }

    this.deleteMe = this.deleteMe.bind(this)
  }

  deleteMe() {
    const cmd = {
      type: "LIST",
      cmd: "DELETE",
      data: {
        name: this.state.name
      }
    };

    this.state.ws.send(JSON.stringify(cmd));
  }

  render() {
    return (
      <div className="List">
        <p>List: {this.state.name}</p>
        <button onClick={this.deleteMe}>
          Delete this list
        </button>
      </div>
    )
  }
};

List.propTypes = {
  name: PropTypes.string.isRequired,
  ws: PropTypes.object.isRequired,
}

export default List;
