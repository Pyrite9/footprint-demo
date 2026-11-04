import { Link } from 'react-router'
import Icon from './Icon'

// 조 홈의 "내가 할 수 있는 일"을 그립니다. 누르면 그 일을 하는 화면으로 갑니다. 데이터는 page에서 props로 받습니다.
// state: 옮겨 간 화면에 값을 넘깁니다. 받는 화면은 useLocation()으로 꺼내 그 줄을 바로 엽니다.
function TeamTodoList({ todos }) {
    return (
        <section className="card pad">
            <h2 className="title">내가 할 수 있는 일</h2>
            {todos.length === 0 && <p className="sub">지금 할 일은 없습니다.</p>}
            {todos.length > 0 && (
                <div>
                    {todos.map((todo) => (
                        <Link key={todo.text} to={todo.to} state={{ selectedId: todo.selectedId }} className="todo">
                            <span className="todo-text">{todo.text}</span>
                            <Icon name="chevron" />
                        </Link>
                    ))}
                </div>
            )}
        </section>
    )
}

export default TeamTodoList
